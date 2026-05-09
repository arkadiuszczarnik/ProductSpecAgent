"use client";

import { useState } from "react";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import type { WizardOption, WizardOptionField } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

interface WizardOptionFieldEditorProps {
  field: WizardOptionField;
  disabled?: boolean;
  onChange: (field: WizardOptionField) => void;
}

function slugifyOptionLabel(label: string): string {
  return label
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function nextOptionId(label: string, options: WizardOption[]): string {
  const baseSlug = slugifyOptionLabel(label);
  const existingIds = new Set(options.map((option) => option.id));

  if (!existingIds.has(baseSlug)) {
    return baseSlug;
  }

  let index = 2;
  let candidate = `${baseSlug}-${index}`;
  while (existingIds.has(candidate)) {
    index += 1;
    candidate = `${baseSlug}-${index}`;
  }
  return candidate;
}

function replaceOption(
  options: WizardOption[],
  index: number,
  nextOption: WizardOption,
): WizardOption[] {
  return options.map((option, optionIndex) => (
    optionIndex === index ? nextOption : option
  ));
}

export function WizardOptionFieldEditor({
  field,
  disabled = false,
  onChange,
}: WizardOptionFieldEditorProps) {
  const [newLabel, setNewLabel] = useState("");
  const trimmedNewLabel = newLabel.trim();
  const nextNewOptionId = nextOptionId(trimmedNewLabel, field.options);
  const canAddOption = Boolean(trimmedNewLabel) && Boolean(nextNewOptionId) && !disabled;

  function updateOptions(options: WizardOption[]) {
    if (disabled) return;
    onChange({ ...field, options });
  }

  function handleAddOption() {
    const label = trimmedNewLabel;
    const optionId = nextOptionId(label, field.options);
    if (!label || !optionId || disabled) return;

    updateOptions([
      ...field.options,
      {
        id: optionId,
        label,
        enabled: true,
      },
    ]);
    setNewLabel("");
  }

  function handleMove(index: number, direction: -1 | 1) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= field.options.length) return;

    const nextOptions = [...field.options];
    const current = nextOptions[index];
    nextOptions[index] = nextOptions[targetIndex];
    nextOptions[targetIndex] = current;
    updateOptions(nextOptions);
  }

  return (
    <div className="border-t py-3 first:border-t-0">
      <div className="mb-2 flex items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold">{field.label}</h3>
          <p className="text-xs text-muted-foreground">
            {field.step} / {field.key}
          </p>
        </div>
        <div className="flex min-w-52 items-end gap-2">
          <div className="flex-1">
            <Label htmlFor={`${field.step}-${field.key}-new`} className="sr-only">
              Neue Option
            </Label>
            <Input
              id={`${field.step}-${field.key}-new`}
              value={newLabel}
              onChange={(event) => setNewLabel(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && canAddOption) {
                  event.preventDefault();
                  handleAddOption();
                }
              }}
              placeholder="Neue Option"
              disabled={disabled}
            />
          </div>
          <Button
            type="button"
            size="icon"
            variant="outline"
            onClick={handleAddOption}
            disabled={!canAddOption}
            aria-label="Option hinzufuegen"
            title="Option hinzufuegen"
          >
            <Plus size={15} />
          </Button>
        </div>
      </div>

      <div className="overflow-hidden rounded-md border">
        <div className="grid grid-cols-[minmax(10rem,1fr)_8rem_8rem_6rem] border-b bg-muted/40 px-2 py-1.5 text-xs font-medium text-muted-foreground">
          <div>Label</div>
          <div>Status</div>
          <div>Reihenfolge</div>
          <div className="text-right">Aktion</div>
        </div>
        {field.options.length === 0 ? (
          <div className="px-2 py-4 text-sm text-muted-foreground">
            Keine Optionen.
          </div>
        ) : (
          field.options.map((option, index) => (
            <div
              key={option.id}
              className={cn(
                "grid grid-cols-[minmax(10rem,1fr)_8rem_8rem_6rem] items-center gap-2 border-b px-2 py-2 last:border-b-0",
                option.enabled === false && "bg-muted/20 text-muted-foreground",
              )}
            >
              <div className="min-w-0">
                <Input
                  value={option.label}
                  onChange={(event) => {
                    updateOptions(replaceOption(field.options, index, {
                      ...option,
                      label: event.target.value,
                    }));
                  }}
                  aria-label={`Label fuer ${option.id}`}
                  disabled={disabled}
                />
                <div className="mt-1 truncate text-[11px] text-muted-foreground">
                  {option.id}
                </div>
              </div>
              <label className="flex items-center gap-2 text-xs">
                <input
                  type="checkbox"
                  checked={option.enabled !== false}
                  onChange={(event) => {
                    updateOptions(replaceOption(field.options, index, {
                      ...option,
                      enabled: event.target.checked,
                    }));
                  }}
                  className="size-4 rounded border-input"
                  disabled={disabled}
                  aria-label={`${option.label} aktiv`}
                />
                Aktiv
              </label>
              <div className="flex items-center gap-1">
                <Button
                  type="button"
                  size="icon-xs"
                  variant="ghost"
                  onClick={() => handleMove(index, -1)}
                  disabled={disabled || index === 0}
                  aria-label={`${option.label} nach oben`}
                  title="Nach oben"
                >
                  <ArrowUp size={14} />
                </Button>
                <Button
                  type="button"
                  size="icon-xs"
                  variant="ghost"
                  onClick={() => handleMove(index, 1)}
                  disabled={disabled || index === field.options.length - 1}
                  aria-label={`${option.label} nach unten`}
                  title="Nach unten"
                >
                  <ArrowDown size={14} />
                </Button>
              </div>
              <div className="flex justify-end">
                <Button
                  type="button"
                  size="icon-xs"
                  variant="ghost"
                  onClick={() => {
                    updateOptions(field.options.filter((_, optionIndex) => (
                      optionIndex !== index
                    )));
                  }}
                  disabled={disabled}
                  aria-label={`${option.label} entfernen`}
                  title="Entfernen"
                >
                  <Trash2 size={14} />
                </Button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
