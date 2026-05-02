"use client";

interface Props {
  id: string;
  onChange: () => void;
}

export function PromptDetail({ id }: Props) {
  return <div className="p-4 text-sm">Prompt {id} (Editor folgt)</div>;
}
