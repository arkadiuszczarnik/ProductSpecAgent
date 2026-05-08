/* global React */
// All UI primitives + composites for the Spec Agent kit.
// Exported to window at the bottom so other Babel scripts can use them.

const { useState, useEffect, useRef } = React;

// ---------- helpers --------------------------------------------------

const cx = (...a) => a.filter(Boolean).join(" ");

// Inline lucide SVGs — only the ones the kit actually renders.
const Icon = ({ name, size = 16, className = "", strokeWidth = 1.5 }) => {
  const svgs = {
    sparkles: (<><path d="m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3z"/><path d="M5 3v4"/><path d="M19 17v4"/><path d="M3 5h4"/><path d="M17 19h4"/></>),
    "folder-kanban": (<><path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/><path d="M8 10v4"/><path d="M12 10v2"/><path d="M16 10v6"/></>),
    plus: (<><path d="M5 12h14"/><path d="M12 5v14"/></>),
    cpu: (<><rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><path d="M15 2v2"/><path d="M15 20v2"/><path d="M2 15h2"/><path d="M2 9h2"/><path d="M20 15h2"/><path d="M20 9h2"/><path d="M9 2v2"/><path d="M9 20v2"/></>),
    settings: (<><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></>),
    bot: (<><path d="M12 8V4H8"/><rect width="16" height="12" x="4" y="8" rx="2"/><path d="M2 14h2"/><path d="M20 14h2"/><path d="M15 13v2"/><path d="M9 13v2"/></>),
    user: (<><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></>),
    send: (<><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></>),
    "arrow-left": (<><path d="m12 19-7-7 7-7"/><path d="M19 12H5"/></>),
    "arrow-right": (<><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></>),
    check: (<><polyline points="20 6 9 17 4 12"/></>),
    x: (<><path d="M18 6 6 18"/><path d="m6 6 12 12"/></>),
    star: (<><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></>),
    info: (<><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></>),
    "alert-triangle": (<><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4"/><path d="M12 17h.01"/></>),
    "chevron-right": (<><path d="m9 18 6-6-6-6"/></>),
    "chevron-left": (<><path d="m15 18-6-6 6-6"/></>),
    "chevron-down": (<><path d="m6 9 6 6 6-6"/></>),
    "help-circle": (<><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><path d="M12 17h.01"/></>),
    "check-circle-2": (<><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></>),
    download: (<><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/></>),
    "file-text": (<><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" x2="8" y1="13" y2="13"/><line x1="16" x2="8" y1="17" y2="17"/><line x1="10" x2="8" y1="9" y2="9"/></>),
    "more-horizontal": (<><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></>),
    layers: (<><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></>),
    "book-open": (<><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></>),
    activity: (<><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></>),
    package: (<><path d="m7.5 4.27 9 5.15"/><path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/><path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/></>),
    "shield-check": (<><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10"/><path d="m9 12 2 2 4-4"/></>),
    "folder-tree": (<><path d="M20 10a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1h-2.9a1 1 0 0 1-.88-.55l-.42-.85A1 1 0 0 0 14.9 3H11a1 1 0 0 0-1 1v5a1 1 0 0 0 1 1Z"/><path d="M20 21a1 1 0 0 0 1-1v-3a1 1 0 0 0-1-1h-2.9a1 1 0 0 1-.88-.55l-.42-.85a1 1 0 0 0-.88-.6H11a1 1 0 0 0-1 1v5a1 1 0 0 0 1 1Z"/><path d="M3 5a2 2 0 0 0 2 2h3"/><path d="M3 3v13a2 2 0 0 0 2 2h3"/></>),
  };
  const path = svgs[name] || null;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round"
      className={className}>{path}</svg>
  );
};

// ---------- primitives -----------------------------------------------

const Button = ({ variant = "primary", size = "default", children, leading, trailing, className, ...rest }) => {
  const base = "btn";
  return (
    <button className={cx(base, `btn--${variant}`, size === "sm" && "btn--sm", size === "icon" && "btn--icon", className)} {...rest}>
      {leading}{children}{trailing}
    </button>
  );
};

const Badge = ({ tone = "neutral", children, className }) => (
  <span className={cx("badge", `badge--${tone}`, className)}>{children}</span>
);

const Input = ({ invalid, className, ...rest }) => (
  <input className={cx("inp", invalid && "inp--invalid", className)} {...rest} />
);

const Textarea = ({ className, rows = 3, ...rest }) => (
  <textarea className={cx("inp inp--ta", className)} rows={rows} {...rest} />
);

const Avatar = ({ initials, tone = "neutral", icon }) => (
  <div className={cx("avatar", `avatar--${tone}`)}>
    {icon ? <Icon name={icon} size={14}/> : initials}
  </div>
);

const Card = ({ children, className, hoverable, onClick }) => (
  <div className={cx("card-ui", hoverable && "card-ui--hover", className)} onClick={onClick}>{children}</div>
);

const Progress = ({ value }) => (
  <div className="progress"><i style={{ width: `${Math.min(100, Math.max(0, value))}%` }}/></div>
);

// ---------- shell ----------------------------------------------------

const IconRail = ({ active, onNav }) => {
  const items = [
    { id: "dashboard", icon: "folder-kanban", label: "Projekte" },
    { id: "models",    icon: "cpu",           label: "Agent-Modelle" },
    { id: "library",   icon: "package",       label: "Asset Bundles" },
  ];
  return (
    <aside className="rail">
      <div className="rail__logo" style={{ display: "none" }}>
        <svg viewBox="0 0 189 163" fill="none" xmlns="http://www.w3.org/2000/svg" aria-label="Spec Agent">
          <defs>
            <linearGradient id="rDocStroke" x1="56" y1="3" x2="159" y2="145" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#6C7CFF"/><stop offset="0.42" stopColor="#5177FF"/>
              <stop offset="0.74" stopColor="#128FE7"/><stop offset="1" stopColor="#10B9F0"/>
            </linearGradient>
            <linearGradient id="rSparkFill" x1="25" y1="63" x2="89" y2="132" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#8B6DFF"/><stop offset="0.5" stopColor="#7A62F2"/><stop offset="1" stopColor="#5E74FF"/>
            </linearGradient>
            <linearGradient id="rLineStroke" x1="105" y1="72" x2="162" y2="72" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#8A67FF"/><stop offset="1" stopColor="#4777FF"/>
            </linearGradient>
          </defs>
          <g>
            <path d="M65 57V11C65 6.58 68.58 3 73 3H129L176 50V140C176 144.42 172.42 148 168 148H76C71.58 148 68 144.42 68 140V134" stroke="url(#rDocStroke)" strokeWidth="9" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M130 4V42C130 46.42 133.58 50 138 50H176" stroke="url(#rDocStroke)" strokeWidth="9" strokeLinecap="round" strokeLinejoin="round"/>
          </g>
          <g stroke="url(#rLineStroke)" strokeWidth="9" strokeLinecap="round">
            <path d="M108 75H156"/><path d="M108 99H156"/><path d="M108 124H156"/>
          </g>
          <g className="big-spark">
            <path d="M48.5 54.5C55.1 76.9 66.6 88.2 88.5 94.5C66.6 100.8 55.1 112.1 48.5 134.5C41.9 112.1 30.4 100.8 8.5 94.5C30.4 88.2 41.9 76.9 48.5 54.5Z" fill="url(#rSparkFill)"/>
          </g>
          <g fill="#8268F7">
            <path className="diamond d1" d="M4.75 79L9.5 83.75L4.75 88.5L0 83.75L4.75 79Z"/>
            <path className="diamond d2" d="M30.4 58L35.2 62.8L30.4 67.6L25.6 62.8L30.4 58Z"/>
            <path className="diamond d3" d="M4.75 111L9.5 115.75L4.75 120.5L0 115.75L4.75 111Z"/>
            <path className="diamond d4" d="M27.4 126L31.8 130.4L27.4 134.8L23 130.4L27.4 126Z"/>
          </g>
        </svg>
      </div>
      <nav className="rail__nav">
        {items.map(it => (
          <button key={it.id}
            className={cx("rail__btn", active === it.id && "rail__btn--active")}
            title={it.label}
            onClick={() => onNav(it.id)}>
            <Icon name={it.icon} size={20}/>
          </button>
        ))}
      </nav>
      <div className="rail__foot">
        <button className="rail__btn" title="Einstellungen"><Icon name="settings" size={20}/></button>
      </div>
    </aside>
  );
};

// ---------- dashboard ------------------------------------------------

const ProjectCard = ({ p, onOpen }) => (
  <Card hoverable onClick={() => onOpen(p)} className="proj-card">
    <div className="proj-card__head">
      <div className="proj-card__icon"><Icon name="sparkles" size={16}/></div>
      <button className="proj-card__menu" onClick={(e)=>e.stopPropagation()}><Icon name="more-horizontal" size={16}/></button>
    </div>
    <h3 className="proj-card__title">{p.title}</h3>
    <div className="proj-card__meta">{p.date} · {p.category}</div>
    <Progress value={(p.step / 8) * 100}/>
    <div className="proj-card__row">
      <Badge tone={p.step === 8 ? "success" : "primary"}>{p.step}/8 steps</Badge>
      <Badge tone="neutral">{p.currentStep}</Badge>
    </div>
  </Card>
);

const DashboardView = ({ projects, onOpen, onNew }) => (
  <div className="page">
    <header className="page__header">
      <div>
        <h1 className="page__title">Projekte</h1>
        <p className="page__sub">{projects.length} projects — turn ideas into specs.</p>
      </div>
      <Button variant="primary" leading={<Icon name="plus" size={14}/>} onClick={onNew}>New Project</Button>
    </header>
    <div className="proj-grid">
      {projects.map(p => <ProjectCard key={p.id} p={p} onOpen={onOpen}/>)}
      <button className="proj-card proj-card--new" onClick={onNew}>
        <Icon name="plus" size={20}/>
        <span>Neues Projekt anlegen</span>
      </button>
    </div>
  </div>
);

// ---------- wizard ---------------------------------------------------

const WIZARD_STEPS = [
  { key: "IDEA",        label: "Idee" },
  { key: "PROBLEM",     label: "Problem" },
  { key: "FEATURES",    label: "Features" },
  { key: "MVP",         label: "MVP" },
  { key: "DESIGN",      label: "Design" },
  { key: "ARCHITECTURE",label: "Architektur" },
  { key: "BACKEND",     label: "Backend" },
  { key: "FRONTEND",    label: "Frontend" },
];

const WizardStepper = ({ stepIdx, onStep }) => (
  <div className="stepper">
    {WIZARD_STEPS.map((s, i) => {
      const state = i < stepIdx ? "done" : i === stepIdx ? "active" : "todo";
      return (
        <React.Fragment key={s.key}>
          <button className={cx("stepper__step", `stepper__step--${state}`)} onClick={() => onStep && onStep(i)}>
            <span className="stepper__dot">
              {state === "done" ? <Icon name="check" size={12} strokeWidth={3}/> : i + 1}
            </span>
            <span className="stepper__label">{s.label}</span>
          </button>
          {i < WIZARD_STEPS.length - 1 && <div className={cx("stepper__line", i < stepIdx && "stepper__line--done")}/>}
        </React.Fragment>
      );
    })}
  </div>
);

const WizardHeader = ({ project, stepIdx, onExit, children }) => {
  return (
    <header className="wiz__head">
      <div className="wiz__stepper-row">{children}</div>
    </header>
  );
};

const ProjectTopbar = ({ project, onExit, panelCollapsed, onTogglePanel }) => {
  return (
    <div className="ptopbar">
      <div className="ptopbar__crumb">
        <button className="link" onClick={onExit}><Icon name="arrow-left" size={13}/> Projekte</button>
        <span className="sep">/</span>
        <span className="proj">{project.title}</span>
      </div>
      <div className="ptopbar__actions">
        <button
          className={cx("ptopbar__toggle", !panelCollapsed && "ptopbar__toggle--on")}
          onClick={onTogglePanel}
          title={panelCollapsed ? "Chat & Clarifications einblenden" : "Chat & Clarifications ausblenden"}
          aria-label={panelCollapsed ? "Chat & Clarifications einblenden" : "Chat & Clarifications ausblenden"}>
          <Icon name="bot" size={14}/>
          <Icon name={panelCollapsed ? "chevron-left" : "chevron-right"} size={13}/>
        </button>
      </div>
    </div>
  );
};

const WizardFooter = ({ stepIdx, onBack, onNext }) => {
  const isLast = stepIdx === WIZARD_STEPS.length - 1;
  return (
    <footer className="wiz__foot">
      <span className="wiz__step-meta">Schritt {stepIdx + 1} von {WIZARD_STEPS.length}</span>
      <div className="wiz__cta">
        {stepIdx > 0 && (
          <Button variant="ghost" size="sm" leading={<Icon name="arrow-left" size={13}/>} onClick={onBack}>Zurück</Button>
        )}
        <Button variant="primary" trailing={<Icon name={isLast ? "check" : "arrow-right"} size={14}/>} onClick={onNext}>
          {isLast ? "Spec abschließen" : "Weiter"}
        </Button>
      </div>
    </footer>
  );
};

// Step body content — keyed by step.key; falls back to a generic stub.
const stepBodies = {
  IDEA: {
    title: "Idee definieren",
    body: () => (
      <div className="form">
        <div className="field">
          <label>Projekt-Titel</label>
          <Input defaultValue="Bromo Control Hub" />
        </div>
        <div className="field">
          <label>Kurzbeschreibung</label>
          <Textarea defaultValue="Ein Web-Dashboard, mit dem das Bromo-Team alle Outbound-Mailings, Templates und Empfänger-Listen aus einem Ort steuern kann." rows={3}/>
        </div>
        <div className="field">
          <label>Kategorie</label>
          <div className="chip-row">
            {["web-app", "mobile-app", "internal-tool", "api", "data-pipeline", "browser-ext"].map(c => (
              <span key={c} className={cx("chip", c === "web-app" && "chip--on")}>{c}</span>
            ))}
          </div>
        </div>
      </div>
    ),
  },
  PROBLEM: {
    title: "Problem & Zielgruppe",
    body: () => (
      <div className="form">
        <div className="field">
          <label>Welches Problem löst das Produkt?</label>
          <Textarea defaultValue="Mailings landen heute über drei Tools verteilt: Brevo, ein Google Sheet und Slack-Threads. Jeder Versand ist ein manueller Abgleich." rows={3}/>
        </div>
        <div className="field">
          <label>Primäre Zielgruppe</label>
          <Input defaultValue="Marketing- und CRM-Team bei Bromo (5 Personen)"/>
        </div>
      </div>
    ),
  },
  FEATURES: {
    title: "Features priorisieren",
    body: () => (
      <div className="features">
        {[
          { name: "Template-Editor", tone: "primary",  badge: "MUST" },
          { name: "Empfänger-Segmentierung", tone: "primary", badge: "MUST" },
          { name: "A/B-Versand", tone: "neutral", badge: "SHOULD" },
          { name: "Webhook → Slack", tone: "neutral", badge: "SHOULD" },
          { name: "KI-Subject-Lines", tone: "purple",  badge: "COULD" },
        ].map(f => (
          <div className="feat" key={f.name}>
            <div className="feat__name">{f.name}</div>
            <Badge tone={f.tone}>{f.badge}</Badge>
          </div>
        ))}
      </div>
    ),
  },
  FRONTEND: {
    title: "Frontend definieren",
    body: () => (
      <div className="form">
        <div className="form__row">
          <div className="field">
            <label>Framework</label>
            <div className="chip-row">
              {["Next.js", "Remix", "Astro", "SvelteKit"].map(c => (
                <span key={c} className={cx("chip", c === "Next.js" && "chip--on")}>{c}</span>
              ))}
            </div>
          </div>
          <div className="field">
            <label>Style</label>
            <div className="chip-row">
              {["Tailwind 4", "CSS Modules", "Vanilla Extract"].map(c => (
                <span key={c} className={cx("chip", c === "Tailwind 4" && "chip--on")}>{c}</span>
              ))}
            </div>
          </div>
        </div>
        <div className="field">
          <label>Wichtige Routen</label>
          <Textarea rows={5} defaultValue={"/dashboard\n/projects/[id]\n/projects/[id]/specs/[slug]\n/settings"}/>
        </div>
      </div>
    ),
  },
};
const fallbackBody = (step) => ({
  title: step.label + " definieren",
  body: () => <div className="empty"><Icon name="file-text" size={32}/><h3>{step.label}</h3><p>Schritt-Inhalt wird live aus dem Spec Agent generiert.</p></div>,
});

const WizardBody = ({ stepIdx }) => {
  const step = WIZARD_STEPS[stepIdx];
  const def = stepBodies[step.key] || fallbackBody(step);
  const Body = def.body;
  return (
    <div className="wiz__body">
      <Body/>
    </div>
  );
};

// ---------- right panel ----------------------------------------------

const TabBar = ({ tabs, active, onSelect }) => (
  <div className="tabs">
    {tabs.map(t => (
      <button key={t.id}
        className={cx("tabs__tab", active === t.id && "tabs__tab--active")}
        onClick={() => onSelect(t.id)}>
        {t.label}
        {t.count != null && <span className="tabs__count">{t.count}</span>}
      </button>
    ))}
  </div>
);

const ChatBubble = ({ from, children }) => (
  <div className={cx("chat__row", from === "user" && "chat__row--mine")}>
    {from === "bot" && <Avatar tone="primary" icon="bot"/>}
    <div className={cx("chat__bubble", from === "user" ? "chat__bubble--mine" : "chat__bubble--bot")}>{children}</div>
    {from === "user" && <Avatar tone="neutral" initials="JR"/>}
  </div>
);

const ChatPanel = ({ messages, onSend }) => {
  const [draft, setDraft] = useState("");
  const ref = useRef();
  useEffect(() => { if (ref.current) ref.current.scrollTop = ref.current.scrollHeight; }, [messages]);
  const submit = () => {
    if (!draft.trim()) return;
    onSend(draft.trim()); setDraft("");
  };
  return (
    <div className="chat">
      <div className="chat__list" ref={ref}>
        {messages.map((m, i) => {
          const cont = i > 0 && messages[i - 1].from === m.from;
          return (
            <div key={i} className={cx("chat__row", m.from === "user" && "chat__row--mine", cont && "chat__row--cont")}>
              {m.from === "bot"  && <Avatar tone="primary" icon="bot"/>}
              <div className={cx("chat__bubble", m.from === "user" ? "chat__bubble--mine" : "chat__bubble--bot")}>{m.text}</div>
              {m.from === "user" && <Avatar tone="neutral" initials="JR"/>}
            </div>
          );
        })}
      </div>
      <div className="chat__compose">
        <Textarea rows={1} placeholder="Provide your answer…"
          value={draft}
          onChange={e=>setDraft(e.target.value)}
          onKeyDown={(e)=>{ if (e.key==="Enter" && !e.shiftKey) { e.preventDefault(); submit(); } }}/>
        <Button variant="primary" size="icon" onClick={submit}><Icon name="send" size={14}/></Button>
      </div>
    </div>
  );
};

const DecisionCard = ({ d, onConfirm }) => (
  <div className={cx("decision", d.recommended && "decision--rec")}>
    <div className="decision__head">
      <span className="decision__title">{d.title}</span>
      {d.recommended && <Badge tone="primary"><Icon name="star" size={11}/> AI Recommendation</Badge>}
    </div>
    <p className="decision__rationale">{d.rationale}</p>
    <div className="decision__row">
      <Badge tone={d.status === "Resolved" ? "success" : "primary"}>{d.status}</Badge>
      {d.status !== "Resolved" && <Button variant="outline" size="sm" onClick={()=>onConfirm(d.id)}>Confirm</Button>}
    </div>
  </div>
);

const ClarificationItem = ({ c, onResolve }) => (
  <div className={cx("clarif", c.status === "resolved" && "clarif--done")}>
    <div className="clarif__head">
      <Icon name={c.status === "resolved" ? "check-circle-2" : "help-circle"} size={14}/>
      <span>{c.q}</span>
    </div>
    {c.status === "open" && (
      <div className="clarif__body">
        <Textarea rows={2} placeholder="Antwort eingeben…"/>
        <Button variant="primary" size="sm" onClick={()=>onResolve(c.id)}>Beantworten</Button>
      </div>
    )}
  </div>
);

const TasksList = ({ tasks }) => (
  <div className="tasks">
    {tasks.length === 0 ? (
      <div className="empty">
        <Icon name="check-circle-2" size={32}/>
        <h3>No tasks yet</h3>
        <p>Generate a plan from your spec to create tasks.</p>
        <Button variant="primary" leading={<Icon name="sparkles" size={14}/>}>Plan generieren</Button>
      </div>
    ) : tasks.map(t => (
      <div key={t.id} className="task">
        <span className={cx("task__check", t.done && "task__check--on")}>
          {t.done && <Icon name="check" size={11} strokeWidth={3}/>}
        </span>
        <div className="task__body">
          <div className="task__title">{t.title}</div>
          <div className="task__meta">{t.epic} · {t.estimate}</div>
        </div>
        <Badge tone={t.done ? "success" : "neutral"}>{t.done ? "Done" : "Open"}</Badge>
      </div>
    ))}
  </div>
);

const RightPanel = ({ project, messages, onSend, decisions, onConfirmDecision, clarifications, onResolveClarif, tasks, collapsed = false }) => {
  const [tab, setTab] = useState("chat");
  const [width, setWidth] = useState(400);
  const dragRef = useRef(null);
  const onDrag = (e) => {
    const startX = e.clientX; const startW = width;
    const move = (ev) => setWidth(Math.max(320, Math.min(640, startW + (startX - ev.clientX))));
    const up = () => { window.removeEventListener("mousemove", move); window.removeEventListener("mouseup", up); };
    window.addEventListener("mousemove", move); window.addEventListener("mouseup", up);
  };
  const tabs = [
    { id: "chat",   label: "Chat" },
    { id: "decisions",     label: "Decisions",     count: decisions.length },
    { id: "clarifications",label: "Clarifications",count: clarifications.filter(c=>c.status==="open").length },
    { id: "tasks", label: "Tasks", count: tasks.length },
    { id: "documents", label: "Documents" },
    { id: "sync", label: "Sync" },
  ];
  if (collapsed) {
    return null;
  }
  return (
    <aside className="rpanel" style={{ width }}>
      <div className="rpanel__resize" ref={dragRef} onMouseDown={onDrag}/>
      <TabBar tabs={tabs} active={tab} onSelect={setTab}/>
      <div className="rpanel__body">
        {tab === "chat" && <ChatPanel messages={messages} onSend={onSend}/>}
        {tab === "decisions" && (
          <div className="decisions">
            {decisions.map(d => <DecisionCard key={d.id} d={d} onConfirm={onConfirmDecision}/>)}
          </div>
        )}
        {tab === "clarifications" && (
          <div className="clarifs">
            {clarifications.map(c => <ClarificationItem key={c.id} c={c} onResolve={onResolveClarif}/>)}
          </div>
        )}
        {tab === "tasks" && <TasksList tasks={tasks}/>}
        {tab === "documents" && (
          <div className="empty">
            <Icon name="file-text" size={32}/>
            <h3>Keine Dokumente</h3>
            <p>Lade Markdown- oder Text-Dateien hoch, um sie dem Spec-Kontext hinzuzufügen.</p>
            <Button variant="outline" leading={<Icon name="download" size={14}/>}>Datei hochladen</Button>
          </div>
        )}
        {tab === "sync" && (
          <div className="empty">
            <Icon name="shield-check" size={32}/>
            <h3>Sync</h3>
            <p>Pushe Spec, Decisions und Tasks an dein Issue-Tracker.</p>
          </div>
        )}
      </div>
    </aside>
  );
};

// ---------- export ---------------------------------------------------

Object.assign(window, {
  Icon, Button, Badge, Input, Textarea, Avatar, Card, Progress,
  IconRail, ProjectCard, DashboardView,
  WIZARD_STEPS, WizardStepper, WizardHeader, WizardFooter, WizardBody, ProjectTopbar,
  TabBar, ChatBubble, ChatPanel, DecisionCard, ClarificationItem, TasksList,
  RightPanel,
});
