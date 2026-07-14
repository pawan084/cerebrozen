import nodemailer from "nodemailer";
import { site } from "@/lib/site";

const MAX_FIELD = 200;
const MAX_MESSAGE = 4000;

function field(value: unknown, max = MAX_FIELD): string {
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}

export async function POST(request: Request) {
  let body: Record<string, unknown>;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "Invalid request body." }, { status: 400 });
  }

  // Honeypot: bots fill every input; real users never see this field.
  if (field(body.website)) {
    return Response.json({ ok: true });
  }

  const name = field(body.name);
  const email = field(body.email);
  const company = field(body.company);
  const size = field(body.size);
  const message = field(body.message, MAX_MESSAGE);

  if (!name || !company || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return Response.json(
      { error: "Please provide your name, a valid email, and your company." },
      { status: 400 }
    );
  }

  // Primary destination: the platform's demo pipeline (the ops-admin tab).
  // Best-effort — the pipeline being down must not lose a lead while email
  // still works, and vice versa.
  let piped = false;
  const platformUrl = process.env.PLATFORM_API_URL || "http://localhost:8100";
  try {
    const r = await fetch(`${platformUrl}/demo-requests`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, email, company, size, message }),
      signal: AbortSignal.timeout(5000),
    });
    piped = r.ok;
  } catch (err) {
    console.error("Demo pipeline unreachable:", err);
  }

  const { SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS } = process.env;
  if (!SMTP_HOST || !SMTP_USER || !SMTP_PASS) {
    if (piped) return Response.json({ ok: true }); // pipeline has the lead
    console.error("Demo request received but SMTP is not configured.");
    return Response.json(
      { error: "Email delivery is not configured." },
      { status: 503 }
    );
  }

  const transporter = nodemailer.createTransport({
    host: SMTP_HOST,
    port: Number(SMTP_PORT ?? 465),
    secure: Number(SMTP_PORT ?? 465) === 465,
    auth: { user: SMTP_USER, pass: SMTP_PASS },
  });

  try {
    await transporter.sendMail({
      from: `"${site.name} website" <${SMTP_USER}>`,
      to: process.env.DEMO_REQUEST_TO ?? site.email,
      replyTo: `"${name}" <${email}>`,
      subject: `Demo request — ${company}`,
      text: [
        `Name:     ${name}`,
        `Email:    ${email}`,
        `Company:  ${company}`,
        `Size:     ${size || "—"}`,
        "",
        "What they'd like coaching to change:",
        message || "—",
      ].join("\n"),
    });
  } catch (err) {
    console.error("Failed to send demo request email:", err);
    if (piped) return Response.json({ ok: true }); // pipeline has the lead
    return Response.json(
      { error: "We couldn't send your request." },
      { status: 502 }
    );
  }

  return Response.json({ ok: true });
}
