-- Bridge records each run as H.264 → MP4 (faststart) and uploads to MinIO. The public URL
-- is stamped here so the run detail page can render a <video> player. Nullable because
-- recording is best-effort — a bridge/ffmpeg failure must never kill a run.
ALTER TABLE android_automation.runs
    ADD COLUMN video_url TEXT;
