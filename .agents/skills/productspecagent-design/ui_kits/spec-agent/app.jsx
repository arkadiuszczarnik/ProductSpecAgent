/* global React, ReactDOM,
   IconRail, DashboardView, WizardHeader, WizardFooter, WizardStepper, WizardBody, RightPanel,
   ProjectTopbar, WIZARD_STEPS */

const { useState } = React;

const SAMPLE_PROJECTS = [
  { id: "1", title: "Bromo Control Hub", date: "07. Mai 2026", category: "web-app", step: 8, currentStep: "FRONTEND" },
  { id: "2", title: "Newsroom Sync",     date: "05. Mai 2026", category: "web-app", step: 4, currentStep: "DESIGN" },
  { id: "3", title: "Operator Console",  date: "01. Mai 2026", category: "internal-tool", step: 2, currentStep: "PROBLEM" },
];

const SAMPLE_MESSAGES = [
  { from: "bot",  text: "Hallo! Ich bin bereit. Wir sind aktuell im Schritt FRONTEND. Welches Framework soll dein Team nutzen?" },
  { from: "user", text: "Wir wollen bei Next.js bleiben." },
  { from: "bot",  text: "Gute Wahl. Ich füge das in die Decisions hinzu und schlage Tailwind 4 + base-ui als Style-Stack vor — willst du das im Decisions-Tab bestätigen?" },
];

const SAMPLE_DECISIONS = [
  { id: "d1", title: "Frontend-Framework: Next.js 16", rationale: "App-Router, Server Components und next/font matchen den existierenden Stack im Bromo-Repo.", status: "Resolved", recommended: true },
  { id: "d2", title: "UI-Library: base-ui + shadcn/ui (style: base-nova)", rationale: "Headless Primitive mit Tailwind-styling. Erlaubt enges Token-Coupling über CSS-Variablen.", status: "In Progress", recommended: true },
  { id: "d3", title: "Auth: Clerk", rationale: "Schnellstes Setup für Multi-Tenant. Alternative: Supabase Auth (eigener DB-Stack).", status: "Pending", recommended: false },
];

const SAMPLE_CLARIFS = [
  { id: "c1", q: "Welche Browser-Matrix muss unterstützt werden? (Chromium-only? Safari?)", status: "open" },
  { id: "c2", q: "Soll das Dashboard offline-fähig sein?", status: "resolved" },
];

const SAMPLE_TASKS = [
  { id: "t1", title: "AppShell mit Icon-Rail bauen", epic: "Layout", estimate: "S", done: true },
  { id: "t2", title: "Wizard-Stepper implementieren",  epic: "Wizard", estimate: "M", done: true },
  { id: "t3", title: "Right-Panel: Chat-Tab",          epic: "Right Panel", estimate: "M", done: false },
  { id: "t4", title: "Decisions-Modal mit Tag-Suche",  epic: "Decisions",   estimate: "L", done: false },
];

const App = () => {
  const [view, setView] = useState("dashboard"); // 'dashboard' | 'project'
  const [projects, setProjects] = useState(SAMPLE_PROJECTS);
  const [activeProject, setActiveProject] = useState(null);
  const [stepIdx, setStepIdx] = useState(7); // FRONTEND
  const [messages, setMessages] = useState(SAMPLE_MESSAGES);
  const [decisions, setDecisions] = useState(SAMPLE_DECISIONS);
  const [clarifs, setClarifs] = useState(SAMPLE_CLARIFS);
  const [tasks] = useState(SAMPLE_TASKS);
  const [panelCollapsed, setPanelCollapsed] = useState(false);

  const openProject = (p) => {
    setActiveProject(p);
    setStepIdx(WIZARD_STEPS.findIndex(s => s.key === p.currentStep));
    setView("project");
  };
  const back = () => setView("dashboard");
  const newProject = () => {
    const p = { id: String(Date.now()), title: "Neues Projekt", date: "08. Mai 2026", category: "web-app", step: 0, currentStep: "IDEA" };
    setProjects(ps => [p, ...ps]);
    openProject(p);
  };

  const send = (text) => {
    setMessages(m => [...m, { from: "user", text }, { from: "bot", text: "Verstanden — ich füge das dem Spec hinzu." }]);
  };
  const confirmDecision = (id) => {
    setDecisions(ds => ds.map(d => d.id === id ? { ...d, status: "Resolved" } : d));
  };
  const resolveClarif = (id) => {
    setClarifs(cs => cs.map(c => c.id === id ? { ...c, status: "resolved" } : c));
  };

  return (
    <div className="app">
      <IconRail active="dashboard" onNav={() => setView("dashboard")}/>
      {view === "dashboard" ? (
        <main className="workspace">
          <DashboardView projects={projects} onOpen={openProject} onNew={newProject}/>
        </main>
      ) : (
        <main className="workspace workspace--split">
          <ProjectTopbar
            project={activeProject}
            onExit={back}
            panelCollapsed={panelCollapsed}
            onTogglePanel={() => setPanelCollapsed(v => !v)}/>
          <div className="workspace__row">
            <section className="wiz">
              <WizardHeader project={activeProject} stepIdx={stepIdx} onExit={back}>
                <WizardStepper stepIdx={stepIdx} onStep={setStepIdx}/>
              </WizardHeader>
              <div className="wiz__inner">
                <WizardBody stepIdx={stepIdx}/>
              </div>
              <WizardFooter stepIdx={stepIdx}
                onBack={() => setStepIdx(Math.max(0, stepIdx - 1))}
                onNext={() => setStepIdx(Math.min(WIZARD_STEPS.length - 1, stepIdx + 1))}/>
            </section>
            <RightPanel
              project={activeProject}
              messages={messages} onSend={send}
              decisions={decisions} onConfirmDecision={confirmDecision}
              clarifications={clarifs} onResolveClarif={resolveClarif}
              tasks={tasks}
              collapsed={panelCollapsed}/>
          </div>
        </main>
      )}
    </div>
  );
};

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
