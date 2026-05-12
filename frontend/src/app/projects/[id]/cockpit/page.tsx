import { ProjectCockpitPrototype } from "@/components/cockpit/ProjectCockpitPrototype";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function ProjectCockpitPage({ params }: PageProps) {
  const { id } = await params;

  return <ProjectCockpitPrototype projectId={id} />;
}
