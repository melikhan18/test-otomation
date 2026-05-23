package com.qaplatform.android.automation.service;

import com.qaplatform.android.automation.api.dto.AppDtos;
import com.qaplatform.android.automation.domain.AppEntity;
import com.qaplatform.android.automation.domain.AppRepository;
import com.qaplatform.android.automation.domain.AppVersionEntity;
import com.qaplatform.android.automation.domain.AppVersionRepository;
import com.qaplatform.android.automation.service.apps.ApkMetadataReader;
import com.qaplatform.android.automation.service.storage.ObjectStorage;
import com.qaplatform.android.automation.tenancy.ProjectContext;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class AppService {

    private static final Logger log = LoggerFactory.getLogger(AppService.class);

    private final AppRepository apps;
    private final AppVersionRepository versions;
    private final ApkMetadataReader apkReader;
    private final ObjectStorage storage;

    public AppService(AppRepository apps, AppVersionRepository versions,
                      ApkMetadataReader apkReader, ObjectStorage storage) {
        this.apps = apps;
        this.versions = versions;
        this.apkReader = apkReader;
        this.storage = storage;
    }

    /* ───────────────────────────── App CRUD ──────────────────────────────── */

    @Transactional(readOnly = true)
    public List<AppDtos.Summary> list(ProjectContext ctx) {
        List<AppEntity> raw = apps.findAllByProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc(ctx.projectId());
        return raw.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public AppDtos.View get(ProjectContext ctx, long id) {
        return toView(ensureInProject(ctx, id));
    }

    @Transactional
    public AppDtos.View create(JwtPrincipal caller, ProjectContext ctx, AppDtos.CreateRequest req) {
        if (!ctx.canManage()) throw ApiException.forbidden("only OWNER / QA_MANAGER can create apps");

        String pkg = req.packageName().trim();
        validatePackageName(pkg);
        apps.findByProjectIdAndPackageNameAndArchivedAtIsNull(ctx.projectId(), pkg)
                .ifPresent(existing -> { throw ApiException.conflict("an app with this package already exists in this project"); });

        AppEntity app = new AppEntity(ctx.projectId(), pkg, req.displayName().trim(), caller.userId());
        app.setDescription(nullIfBlank(req.description()));
        apps.save(app);
        return toView(app);
    }

    @Transactional
    public AppDtos.View update(ProjectContext ctx, long id, AppDtos.UpdateRequest req) {
        if (!ctx.canManage()) throw ApiException.forbidden("only OWNER / QA_MANAGER can update apps");
        AppEntity app = ensureInProject(ctx, id);
        app.setDisplayName(req.displayName().trim());
        app.setDescription(nullIfBlank(req.description()));
        return toView(app);
    }

    @Transactional
    public void archive(ProjectContext ctx, long id) {
        if (!ctx.canManage()) throw ApiException.forbidden("only OWNER / QA_MANAGER can archive apps");
        AppEntity app = ensureInProject(ctx, id);
        app.setArchivedAt(Instant.now());
    }

    /* ───────────────────────────── Versions ──────────────────────────────── */

    @Transactional(readOnly = true)
    public List<AppDtos.VersionView> listVersions(ProjectContext ctx, long appId) {
        AppEntity app = ensureInProject(ctx, appId);
        return versions.findAllByAppIdOrderByVersionCodeDesc(app.getId()).stream()
                .map(this::toVersionView)
                .toList();
    }

    /**
     * Accept an uploaded APK, parse its manifest, store it in MinIO, and record an
     * {@code app_versions} row. Fails when the APK's packageName doesn't match the
     * app shell, or when this versionCode has already been uploaded.
     *
     * <p>The temp file is materialised on disk so we can:
     * <ol>
     *   <li>parse the manifest with apk-parser (which needs random-access),</li>
     *   <li>compute SHA-256,</li>
     *   <li>stream-upload to MinIO without loading the full APK into the heap.</li>
     * </ol>
     */
    @Transactional
    public AppDtos.VersionView uploadVersion(JwtPrincipal caller, ProjectContext ctx,
                                             long appId, MultipartFile file, String notes) {
        if (!ctx.canManage()) throw ApiException.forbidden("only OWNER / QA_MANAGER can upload APKs");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("file is required");

        AppEntity app = ensureInProject(ctx, appId);

        File temp = null;
        try {
            temp = Files.createTempFile("apk-upload-", ".apk").toFile();
            file.transferTo(temp);

            ApkMetadataReader.ApkMetadata meta = apkReader.read(temp);
            if (!app.getPackageName().equals(meta.packageName())) {
                throw ApiException.badRequest(
                        "APK package mismatch: app expects " + app.getPackageName() +
                        " but file contains " + meta.packageName());
            }

            Optional<AppVersionEntity> existing = versions.findByAppIdAndVersionCode(app.getId(), meta.versionCode());
            if (existing.isPresent()) {
                throw ApiException.conflict("version " + meta.versionCode() + " already uploaded for this app");
            }

            String sha = sha256(temp);
            String key = ctx.projectId() + "/" + app.getId() + "/" + meta.versionCode() + ".apk";
            storage.uploadApk(key, temp);

            AppVersionEntity v = new AppVersionEntity(
                    app.getId(), meta.versionCode(), meta.versionName(),
                    temp.length(), sha, key, caller.userId());
            v.setNotes(nullIfBlank(notes));
            versions.save(v);

            // First upload also seeds the app icon if not yet set.
            if (app.getIconData() == null && meta.iconData() != null) {
                app.setIconData(meta.iconData());
            }
            // touch updatedAt
            apps.save(app);

            log.info("apk uploaded: project={} app={} version={} ({} bytes)",
                    ctx.projectId(), app.getId(), meta.versionCode(), temp.length());
            return toVersionView(v);

        } catch (IOException e) {
            throw ApiException.internal("failed to write temp APK: " + e.getMessage());
        } finally {
            if (temp != null && temp.exists() && !temp.delete()) temp.deleteOnExit();
        }
    }

    @Transactional
    public void deleteVersion(ProjectContext ctx, long appId, long versionId) {
        if (!ctx.canManage()) throw ApiException.forbidden("only OWNER / QA_MANAGER can delete APK versions");
        AppEntity app = ensureInProject(ctx, appId);
        AppVersionEntity v = versions.findById(versionId)
                .orElseThrow(() -> ApiException.notFound("app version"));
        if (!v.getAppId().equals(app.getId())) {
            throw ApiException.badRequest("version belongs to a different app");
        }
        versions.delete(v);
        storage.deleteApk(v.getStorageKey());  // best-effort, deleteApk logs failures
        apps.save(app);  // touch updatedAt
    }

    /* ───────────────────────────── helpers ───────────────────────────────── */

    private AppEntity ensureInProject(ProjectContext ctx, long id) {
        AppEntity a = apps.findById(id).orElseThrow(() -> ApiException.notFound("app"));
        if (!ctx.projectId().equals(a.getProjectId())) {
            throw ApiException.forbidden("app not in active project");
        }
        if (a.getArchivedAt() != null) {
            throw ApiException.notFound("app");  // archived = invisible
        }
        return a;
    }

    private static void validatePackageName(String pkg) {
        // Android package names: ascii letters/digits/underscore, dot-separated,
        // first char of each segment must be a letter. Be lax — APK parse will
        // catch real mismatches.
        if (!pkg.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")) {
            throw ApiException.badRequest("invalid package name: " + pkg);
        }
    }

    private static String nullIfBlank(String s) { return (s == null || s.isBlank()) ? null : s; }

    private static String sha256(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw ApiException.internal("sha256 failed: " + e.getMessage());
        }
    }

    /* ───────────────────────── projection / views ────────────────────────── */

    private AppDtos.Summary toSummary(AppEntity a) {
        Optional<AppVersionEntity> latest = versions.findFirstByAppIdOrderByVersionCodeDesc(a.getId());
        long count = versions.countByAppId(a.getId());
        return new AppDtos.Summary(
                a.getId(), a.getPackageName(), a.getDisplayName(), a.getDescription(), a.getIconData(),
                latest.map(AppVersionEntity::getVersionCode).orElse(null),
                latest.map(AppVersionEntity::getVersionName).orElse(null),
                (int) count,
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }

    private AppDtos.View toView(AppEntity a) {
        List<AppDtos.VersionView> vs = versions.findAllByAppIdOrderByVersionCodeDesc(a.getId()).stream()
                .map(this::toVersionView)
                .toList();
        return new AppDtos.View(
                a.getId(), a.getPackageName(), a.getDisplayName(), a.getDescription(), a.getIconData(),
                a.getCreatedAt(), a.getUpdatedAt(), vs
        );
    }

    private AppDtos.VersionView toVersionView(AppVersionEntity v) {
        return new AppDtos.VersionView(
                v.getId(), v.getAppId(), v.getVersionCode(), v.getVersionName(),
                v.getFileSizeBytes(), v.getSha256(),
                storage.publicUrlForApk(v.getStorageKey()),
                v.getNotes(),
                v.getUploadedByUserId(), v.getUploadedAt()
        );
    }
}
