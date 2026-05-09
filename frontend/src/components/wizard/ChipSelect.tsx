"use client";

import { cn } from "@/lib/utils";

type ChipOption = string | { label: string; value: string };

interface ChipSelectProps {
  options: ChipOption[];
  value: string | string[];
  onChange: (value: string | string[]) => void;
  multiSelect?: boolean;
}

function normalize(opt: ChipOption): { label: string; value: string } {
  return typeof opt === "string" ? { label: opt, value: opt } : opt;
}

export function ChipSelect({ options, value, onChange, multiSelect = false }: ChipSelectProps) {
  const selected = Array.isArray(value) ? value : value ? [value] : [];
  const normalizedOptions = options.map(normalize);
  const knownValues = new Set(normalizedOptions.map((opt) => opt.value));
  const preservedSelected = selected
    .filter((selectedValue) => !knownValues.has(selectedValue))
    .map((selectedValue) => ({
      label: `${selectedValue} (deaktiviert)`,
      value: selectedValue,
    }));
  const normalized = [...normalizedOptions, ...preservedSelected];

  function handleClick(v: string) {
    if (multiSelect) {
      const next = selected.includes(v)
        ? selected.filter((x) => x !== v)
        : [...selected, v];
      onChange(next);
    } else {
      onChange(v);
    }
  }

  return (
    <div className="flex flex-wrap gap-2">
      {normalized.map((opt) => {
        const isSelected = selected.includes(opt.value);
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => handleClick(opt.value)}
            className={cn(
              "rounded-full px-3 py-1.5 text-xs font-medium transition-all",
              isSelected
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground hover:bg-muted/80 hover:text-foreground"
            )}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
