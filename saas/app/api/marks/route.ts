import { NextRequest } from "next/server";
import { z } from "zod";
import { prisma } from "@/src/db/prisma";
import { marksRepo, sheetsRepo, auditRepo } from "@/src/db/prismaRepos";
import { enterMark } from "@/src/services/marks";
import { assertCsrf, jsonError, rateLimited, requireCtx } from "@/src/api/session";
import { serviceErrorToResponse } from "@/src/api/errors";
import { MarkType } from "@/src/grading/engine";

export const dynamic = "force-dynamic";

const Body = z.object({
  schoolId: z.string().min(1),
  studentId: z.string().min(1),
  subjectId: z.string().min(1),
  termId: z.string().min(1),
  componentId: z.string().min(1),
  type: z.enum(["VALUE", "ABS", "MISSING", "EXEMPT", "NA"]),
  score: z.number().min(0).nullable(),
  maxScore: z.number().int().min(1).max(1000).optional(),
});

export async function POST(req: NextRequest) {
  const authed = await requireCtx(req);
  if ("res" in authed) return authed.res;
  const csrf = assertCsrf(req, authed.csrfSecret);
  if (csrf) return csrf;

  const rl = rateLimited(`marks:${authed.ctx.userId}`, 600, 60_000);
  if (rl) return rl;

  const parsed = Body.safeParse(await req.json().catch(() => null));
  if (!parsed.success) {
    return jsonError("VALIDATION", parsed.error.issues[0]?.message ?? "Invalid mark", 400);
  }
  const b = parsed.data;

  // classId and enrollmentId are resolved SERVER-SIDE from the term's
  // academic-year enrollment — the client can never claim a class.
  const db = prisma();
  const term = await db.term.findFirst({ where: { id: b.termId, schoolId: b.schoolId } });
  if (!term) return jsonError("NOT_FOUND", "Term not found", 404);
  const enrollment = await db.enrollment.findFirst({
    where: { schoolId: b.schoolId, studentId: b.studentId, academicYearId: term.academicYearId, status: "ACTIVE" },
  });
  if (!enrollment) return jsonError("NOT_FOUND", "No active enrollment for this student in the term's year", 404);

  try {
    const mark = await enterMark(
      authed.ctx,
      {
        schoolId: b.schoolId,
        studentId: b.studentId,
        subjectId: b.subjectId,
        termId: b.termId,
        componentId: b.componentId,
        enrollmentId: enrollment.id,
        classId: enrollment.classId,
        type: b.type as MarkType,
        score: b.score,
        maxScore: b.maxScore,
      },
      marksRepo(db),
      sheetsRepo(db) as never,
      auditRepo(db)
    );
    return Response.json({ ok: true, mark });
  } catch (e) {
    return serviceErrorToResponse(e);
  }
}

