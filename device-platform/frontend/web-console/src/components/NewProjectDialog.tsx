import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { FolderPlus, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { projectApi, type ProjectCreate } from "@/lib/tenancy";
import { useAuthStore } from "@/store/auth";

type Props = {
  companyId: number;
  companyName: string;
  onClose: () => void;
};

/** Create a project in the given company. Auto-selects the new project after success. */
export default function NewProjectDialog({ companyId, companyName, onClose }: Props) {
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [description, setDescription] = useState("");
  const reloadMemberships = useAuthStore((s) => s.reloadMemberships);
  const setActiveProject = useAuthStore((s) => s.setActiveProject);

  const create = useMutation({
    mutationFn: (b: ProjectCreate) => projectApi.create(companyId, b),
    onSuccess: async (project) => {
      // Refresh the JWT-backed membership cache so the new project shows up in the
      // sidebar switcher, then jump straight into it.
      await reloadMemberships();
      setActiveProject(project.id);
      onClose();
    },
  });

  const err = (create.error as any)?.response?.data?.detail
            ?? (create.error as any)?.response?.data?.message;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold flex items-center gap-2">
              <FolderPlus size={14} className="text-brand-400" />
              New project
            </div>
            <div className="text-[11px] text-ink-muted mt-0.5">in <code className="font-mono">{companyName}</code></div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>

        <div className="p-5 space-y-4">
          <label className="block">
            <span className="label block mb-1.5">Name</span>
            <input
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Web app QA"
              className="input"
            />
          </label>
          <label className="block">
            <span className="label block mb-1.5">
              Slug <span className="text-ink-muted">(optional — auto from name)</span>
            </span>
            <input
              value={slug}
              onChange={(e) => setSlug(e.target.value.toLowerCase())}
              placeholder="web-app"
              className="input font-mono"
              pattern="^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$"
            />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Description <span className="text-ink-muted">(optional)</span></span>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              placeholder="What's this project for?"
              className="input resize-y"
            />
          </label>
          {err && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {String(err)}
            </div>
          )}
        </div>

        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            loading={create.isPending}
            disabled={!name.trim()}
            onClick={() => create.mutate({
              name: name.trim(),
              slug: slug.trim() || undefined,
              description: description.trim() || undefined,
            })}
          >
            Create project
          </Button>
        </div>
      </Card>
    </div>
  );
}
