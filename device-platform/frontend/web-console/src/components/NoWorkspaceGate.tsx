import { useState } from "react";
import { Building2, Plus } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import NewCompanyDialog from "@/components/NewCompanyDialog";
import TopBar from "@/components/TopBar";

/**
 * Rendered in place of any tenant-aware page when the signed-in user belongs to
 * zero companies. Without a company every device/automation endpoint rejects
 * with 400 "missing X-Company-Id", so the only useful action here is to either
 * create a workspace or wait for an invite from an existing admin.
 */
export default function NoWorkspaceGate() {
  const [newCompanyOpen, setNewCompanyOpen] = useState(false);
  return (
    <>
      <TopBar crumbs={[{ label: "Welcome" }]} />
      <div className="flex-1 flex items-center justify-center px-6 py-10">
        <Card className="w-full max-w-xl">
          <EmptyState
            icon={<Building2 size={20} />}
            title="You're not in a workspace yet"
            description="Create a company to enroll devices and run tests — you'll become the OWNER and get a default project. If a teammate is supposed to invite you, ask them to send the invite to your account email."
            action={(
              <Button
                variant="primary"
                size="sm"
                leftIcon={<Plus size={14} />}
                onClick={() => setNewCompanyOpen(true)}
              >
                Create company
              </Button>
            )}
          />
        </Card>
      </div>
      {newCompanyOpen && <NewCompanyDialog onClose={() => setNewCompanyOpen(false)} />}
    </>
  );
}
