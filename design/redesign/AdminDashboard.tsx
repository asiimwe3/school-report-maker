import { useState } from "react";
import { ArrowUpRight, BookOpen, Check, ClipboardCheck, FileText, Plus, Users } from "lucide-react";

import { AdminShell, Button, Donut, PageHead, Pill, SectionTitle, StatCard, SyncNote, TinyChart } from "./_shared/AppShell";
import "./_group.css";

export function AdminDashboard() {
  const [term, setTerm] = useState("Term 3 · 2026");
  const [dismissed, setDismissed] = useState(false);
  return <AdminShell page="dashboard" eyebrow="Overview" title="School overview">
    <PageHead eyebrow="Tuesday, 18 November 2026" title="A clear view of the term." description="Keep marks moving, spot gaps early, and prepare reports without leaving the school office." actions={<><Button kind="quiet" icon={<Plus size={14} />} onClick={() => setDismissed(false)}>Add student</Button><Button kind="gold" icon={<FileText size={14} />} onClick={() => setDismissed(false)}>Generate reports</Button></>} />
    {!dismissed && <div className="srm-note" style={{ marginBottom: 20, display: "flex", justifyContent: "space-between", alignItems: "center" }}><span><b>Term 3 reporting window is open.</b> 6 class markbooks still need a final check before report cards can be generated.</span><button className="srm-link" onClick={() => setDismissed(true)}>Dismiss</button></div>}
    <div className="srm-grid srm-grid-4" style={{ marginBottom: 19 }}>
      <StatCard label="Students on roll" value="1,240" detail={<><b>+34</b> since Term 2</>} icon={Users} />
      <StatCard label="Classes & streams" value="28" detail={<><b>22</b> active markbooks</>} icon={BookOpen} tone="gold" />
      <StatCard label="Marks captured" value="9,840" detail={<><b>87.4%</b> of expected entries</>} icon={ClipboardCheck} />
      <StatCard label="Reports ready" value="186" detail={<><b>+42</b> since yesterday</>} icon={FileText} tone="rust" />
    </div>
    <div className="srm-grid srm-grid-2" style={{ marginBottom: 18 }}>
      <section className="srm-card srm-card-pad">
        <SectionTitle title="Reporting progress" detail="All levels · Term 3, 2026" action={<select className="srm-input" value={term} onChange={(e) => setTerm(e.target.value)} style={{ width: 135, padding: "7px 8px", fontSize: 10 }}><option>Term 3 · 2026</option><option>Term 2 · 2026</option><option>Term 1 · 2026</option></select>} />
        <div style={{ display: "flex", alignItems: "center", gap: 22, padding: "8px 0 14px" }}><Donut value={82} label="captured" /><div style={{ flex: 1 }}><p style={{ fontSize: 11, margin: "0 0 12px", color: "var(--srm-muted)" }}>Entries are moving steadily. <b style={{ color: "var(--srm-ink)" }}>S3 West</b> needs CA marks before its reports can be prepared.</p><div style={{ display: "grid", gap: 9 }}>{[["O-Level", 91], ["A-Level", 76], ["Primary", 84]].map(([label, value]) => <div key={label as string}><div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, marginBottom: 5 }}><span>{label}</span><b>{value}%</b></div><div className="srm-progress"><span style={{ width: `${value}%` }} /></div></div>)}</div></div></div>
        <SyncNote />
      </section>
      <section className="srm-card srm-card-pad">
        <SectionTitle title="School average by term" detail="Across published subject results" action={<Pill tone="teal">+3.8 pts</Pill>} />
        <TinyChart values={[68, 71, 70, 74, 78, 76, 82]} labels={["T1 '24", "T2 '24", "T3 '24", "T1 '25", "T2 '25", "T3 '25", "T3 '26"]} />
        <div style={{ display: "flex", gap: 20, fontSize: 10, color: "var(--srm-muted)" }}><span><i style={{ display: "inline-block", width: 7, height: 7, borderRadius: "50%", background: "var(--srm-teal)", marginRight: 5 }} />school average</span><span>Current: <b style={{ color: "var(--srm-ink)" }}>82%</b></span></div>
      </section>
    </div>
    <section className="srm-card srm-card-pad">
      <SectionTitle title="Class readiness" detail="The next actions for your reporting desk" action={<button className="srm-link" onClick={() => setTerm("All classes")}>View all classes <ArrowUpRight size={12} style={{ verticalAlign: "middle" }} /></button>} />
      <div className="srm-table-wrap"><table className="srm-table"><thead><tr><th>Class & stream</th><th>Teacher</th><th>Marks entered</th><th>Reports</th><th>State</th><th /></tr></thead><tbody>
        {[
          ["S4 East", "P. Mugisha", "100%", "45 / 45", "Ready", "teal"],
          ["P7", "R. Kyomuhendo", "100%", "52 / 52", "Ready", "teal"],
          ["S3 West", "J. Kabarole", "82%", "0 / 40", "Needs CA", "rust"],
          ["S6 North · PCM", "A. Derick", "76%", "18 / 22", "In progress", "gold"],
        ].map(([name, teacher, marks, reports, state, tone]) => <tr key={name}><td><b>{name}</b><div style={{ color: "var(--srm-muted)", fontSize: 9, marginTop: 3 }}>{name === "P7" ? "Primary · 52 learners" : "O-Level · Term 3"}</div></td><td>{teacher}</td><td><div style={{ display: "flex", alignItems: "center", gap: 8 }}><div className="srm-progress" style={{ width: 72 }}><span style={{ width: marks }} /></div>{marks}</div></td><td>{reports}</td><td><Pill tone={tone as "teal" | "gold" | "rust"}>{state}</Pill></td><td><button className="srm-link" onClick={() => setTerm(name)}>Open <ArrowUpRight size={12} style={{ verticalAlign: "middle" }} /></button></td></tr>)}
      </tbody></table></div>
    </section>
    <div style={{ display: "flex", gap: 20, marginTop: 18, color: "var(--srm-muted)", fontSize: 10 }}><span><Check size={13} color="var(--srm-teal)" style={{ verticalAlign: "middle", marginRight: 4 }} />Last USB bundle merged at 09:41</span><span>Next local backup in 18 minutes</span></div>
  </AdminShell>;
}
