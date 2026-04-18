import type { WizardFeatureEdge } from "@/lib/api";

/**
 * Returns true if adding a `from -> to` edge to `existing` would create a cycle.
 * Self-loops count as cycles.
 *
 * Hand-verified cases:
 *   wouldCreateCycle([], "a", "a")                                  === true
 *   wouldCreateCycle([{id:"1",from:"a",to:"b"}], "b", "a")          === true
 *   wouldCreateCycle([{id:"1",from:"a",to:"b"}], "a", "c")          === false
 *   wouldCreateCycle([{id:"1",from:"a",to:"b"},
 *                     {id:"2",from:"b",to:"c"}], "c", "a")          === true
 */
export function wouldCreateCycle(
  existing: WizardFeatureEdge[],
  from: string,
  to: string,
): boolean {
  if (from === to) return true;
  const adj = new Map<string, string[]>();
  for (const e of existing) {
    if (!adj.has(e.from)) adj.set(e.from, []);
    adj.get(e.from)!.push(e.to);
  }
  // With the new edge (from -> to): would we be able to get back to `from` from `to`?
  const visited = new Set<string>();
  const stack = [to];
  while (stack.length) {
    const node = stack.pop()!;
    if (node === from) return true;
    if (visited.has(node)) continue;
    visited.add(node);
    for (const next of adj.get(node) ?? []) stack.push(next);
  }
  return false;
}
