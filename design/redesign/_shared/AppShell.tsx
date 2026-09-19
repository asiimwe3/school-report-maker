import { useState, type ReactNode } from "react";
import {
  Archive,
  BarChart3,
  Bell,
  BookOpen,
  Check,
  ChevronRight,
  ClipboardList,
  CloudOff,
  Download,
  FileText,
  GraduationCap,
  LayoutDashboard,
  Menu,
  Network,
  Printer,
  RefreshCw,
  Settings2,
  ShieldCheck,
  Users,
  Wifi,
  X,
} from "lucide-react";

import "../_group.css";

type AdminPage = "dashboard" | "setup" | "performance" | "reports";

const adminItems: { id: AdminPage; label: string; icon: typeof LayoutDashboard }[] = [
  { id: "dashboard", label: "Overview", icon: LayoutDashboard },
  { id: "setup", label: "Academic setup", icon: Network },
  { id: "performance", label: "Marks & performance", icon: BarChart3 },
  { id: "reports", label: "Reports & templates", icon: FileText },
];

export function AdminShell({
  page,
  eyebrow,
  title,
  children,
}: {
  page: AdminPage;
  eyebrow: string;
  title: string;
  children: ReactNode;
}) {
  const [notice, setNotice] = useState<string | null>(null);
  const announce = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(null), 2600);
  };
  return (
    <div className="srm-root">
      <div className="srm-shell">
        <aside className="srm-sidebar">
          <div className="srm-brand">
            <div className="srm-crest">TS</div>
            <div><strong>School Report</strong><small>Maker · Admin console</small></div>
          </div>
          <div className="srm-nav-label">Workspace</div>
          <nav className="srm-nav" aria-label="Admin navigation">
            {adminItems.map(({ id, label, icon: Icon }) => (
              <button key={id} className={page === id ? "active" : ""} onClick={() => announce(`${label} is ready in this preview`)}>
                <Icon />{label}
              </button>
            ))}
          </nav>
          <div className="srm-nav-label" style={{ marginTop: 25 }}>School records</div>
          <nav className="srm-nav">
            <button onClick={() => announce("Student records opened")}><Users />Students</button>
            <button onClick={() => announce("Grading schemes opened")}><Settings2 />Grading schemes</button>
            <button onClick={() => announce("Sync centre opened")}><RefreshCw />Sync & backup</button>
          </nav>
          <div className="srm-sidebar-foot">
            <div className="srm-profile"><div className="srm-avatar">AD</div><div><span>A. Derick</span><small>Administrator · Offline account</small></div></div>
          </div>
        </aside>
        <main className="srm-main">
          <header className="srm-topbar">
            <div><div className="srm-kicker">Tooro Secondary School</div><div className="srm-top-title">{title}</div></div>
            <div className="srm-top-actions">
              <div className="srm-sync"><span className="srm-sync-dot" />Saved locally · 09:41</div>
              <button className="srm-icon-btn" onClick={() => announce("No new school alerts")} aria-label="Notifications"><Bell size={15} /></button>
              <button className="srm-icon-btn" onClick={() => announce("Menu options opened")} aria-label="Open menu"><Menu size={15} /></button>
            </div>
          </header>
          <div className="srm-content">
            {children}
          </div>
        </main>
      </div>
      {notice && <div style={{ position: "fixed", right: 24, bottom: 24, zIndex: 10, background: "var(--srm-deep)", color: "#fff", borderRadius: 9, padding: "11px 14px", fontSize: 11, boxShadow: "var(--srm-shadow)" }}>{notice}</div>}
    </div>
  );
}

export function PageHead({ eyebrow, title, description, actions }: { eyebrow: string; title: string; description: string; actions?: ReactNode }) {
  return <div className="srm-pagehead"><div><div className="srm-kicker">{eyebrow}</div><h1>{title}</h1><p>{description}</p></div>{actions && <div className="srm-actions">{actions}</div>}</div>;
}

export function Button({ children, kind = "primary", onClick, icon }: { children: ReactNode; kind?: "primary" | "gold" | "quiet"; onClick?: () => void; icon?: ReactNode }) {
  return <button className={`srm-btn srm-btn-${kind}`} onClick={onClick}>{icon}{children}</button>;
}

export function StatCard({ label, value, detail, icon: Icon, tone = "teal" }: { label: string; value: string; detail: ReactNode; icon: typeof Users; tone?: "teal" | "gold" | "rust" }) {
  const bg = tone === "gold" ? "var(--srm-gold-soft)" : tone === "rust" ? "var(--srm-rust-soft)" : "var(--srm-teal-soft)";
  const color = tone === "gold" ? "#8a672d" : tone === "rust" ? "var(--srm-rust)" : "var(--srm-teal)";
  return <div className="srm-card srm-card-pad srm-stat"><div className="srm-stat-top"><span>{label}</span><span className="srm-stat-icon" style={{ background: bg, color }}><Icon size={15} /></span></div><strong>{value}</strong><div className="srm-stat-foot">{detail}</div></div>;
}

export function SectionTitle({ title, detail, action }: { title: string; detail?: string; action?: ReactNode }) {
  return <div className="srm-section-title"><div><h2>{title}</h2>{detail && <p>{detail}</p>}</div>{action}</div>;
}

export function Pill({ children, tone = "teal" }: { children: ReactNode; tone?: "teal" | "gold" | "rust" | "ink" }) {
  return <span className={`srm-pill srm-pill-${tone}`}>{children}</span>;
}

export function SyncNote({ teacher = false }: { teacher?: boolean }) {
  return <div className="srm-note" style={{ display: "flex", alignItems: "center", gap: 9 }}><CloudOff size={15} /><span><b>{teacher ? "Offline-ready marks entry." : "Local-first school records."}</b> Changes are saved on this device and can be pushed by Wi-Fi or imported by USB.</span></div>;
}

export function TinyChart({ values, labels, accent = "#237b71", height = 160 }: { values: number[]; labels: string[]; accent?: string; height?: number }) {
  const max = Math.max(...values, 100);
  const width = 620;
  const padX = 34;
  const padTop = 16;
  const padBottom = 25;
  const innerH = height - padTop - padBottom;
  const points = values.map((v, i) => `${padX + i * ((width - padX * 2) / (values.length - 1))},${padTop + innerH - (v / max) * innerH}`).join(" ");
  return <svg className="srm-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Performance trend chart">
    {[0, 25, 50, 75, 100].map((tick) => { const y = padTop + innerH - (tick / max) * innerH; return <g key={tick}><line x1={padX} x2={width - padX} y1={y} y2={y} stroke="#dfe5df" strokeDasharray="3 5" /><text x="0" y={y + 3} fill="#718078" fontSize="10">{tick}</text></g>; })}
    <polyline points={points} fill="none" stroke={accent} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
    {values.map((v, i) => { const x = padX + i * ((width - padX * 2) / (values.length - 1)); const y = padTop + innerH - (v / max) * innerH; return <g key={labels[i]}><circle cx={x} cy={y} r="4" fill="#fbfaf7" stroke={accent} strokeWidth="3" /><text x={x} y={height - 6} textAnchor="middle" fill="#718078" fontSize="10">{labels[i]}</text></g>; })}
  </svg>;
}

export function Donut({ value = 82, label = "complete" }: { value?: number; label?: string }) {
  const r = 32; const c = 2 * Math.PI * r; const dash = c * value / 100;
  return <div style={{ position: "relative", width: 84, height: 84 }}><svg width="84" height="84" viewBox="0 0 84 84"><circle cx="42" cy="42" r={r} fill="none" stroke="#e4eae3" strokeWidth="8" /><circle cx="42" cy="42" r={r} fill="none" stroke="var(--srm-teal)" strokeWidth="8" strokeLinecap="round" strokeDasharray={`${dash} ${c - dash}`} transform="rotate(-90 42 42)" /></svg><div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center", textAlign: "center" }}><div><b style={{ display: "block", font: "700 15px 'Space Mono', monospace" }}>{value}%</b><small style={{ color: "var(--srm-muted)", fontSize: 8 }}>{label}</small></div></div></div>;
}

export function TeacherShell({ page, children }: { page: "home" | "marks"; children: ReactNode }) {
  const [notice, setNotice] = useState<string | null>(null);
  const announce = (message: string) => { setNotice(message); window.setTimeout(() => setNotice(null), 2400); };
  return <div className="srm-root srm-teacher-wrap">
    <div className="srm-teacher-top"><div className="srm-teacher-top-inner"><div className="srm-teacher-topbar"><div className="srm-teacher-brand"><div className="srm-crest">TS</div>Teacher workspace</div><div className="srm-teacher-status"><span className="srm-sync-dot" />Offline saved</div></div>{page === "home" ? <div className="srm-teacher-hero"><h1>Good morning, Patricia.</h1><p>Your S4 East markbook is up to date for Term 3, 2026.</p></div> : <div className="srm-teacher-hero"><h1>Enter marks</h1><p>S4 East · Mathematics · Term 3</p></div>}</div></div>
    <div className="srm-teacher-body">{children}</div>
    <nav className="srm-bottom-nav"><button className={page === "home" ? "active" : ""} onClick={() => announce("Home selected")}><LayoutDashboard />Home</button><button className={page === "marks" ? "active" : ""} onClick={() => announce("Marks selected")}><ClipboardList />Marks</button><button onClick={() => announce("Student comments selected")}><BookOpen />Comments</button><button onClick={() => announce("Send queue selected")}><Wifi />Send queue</button></nav>
    {notice && <div style={{ position: "fixed", left: "50%", transform: "translateX(-50%)", bottom: 70, zIndex: 10, background: "var(--srm-deep)", color: "#fff", borderRadius: 9, padding: "10px 14px", fontSize: 11 }}>{notice}</div>}
  </div>;
}

export const iconSet = { Archive, Check, ChevronRight, Download, GraduationCap, Printer, ShieldCheck, X };