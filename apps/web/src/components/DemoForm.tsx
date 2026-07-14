"use client";

import { useState, type FormEvent } from "react";
import { CheckIcon } from "@/components/icons";
import { site } from "@/lib/site";

type Status = "idle" | "sending" | "sent" | "error";

export default function DemoForm() {
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState("");

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    setStatus("sending");
    setError("");

    try {
      const data = Object.fromEntries(new FormData(form).entries());
      const res = await fetch("/api/demo", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.error ?? "Something went wrong.");
      }
      setStatus("sent");
    } catch (err) {
      setStatus("error");
      setError(err instanceof Error ? err.message : "Something went wrong.");
    }
  }

  if (status === "sent") {
    return (
      <div className="rounded-3xl border border-zen-100 bg-zen-50 p-10 text-center">
        <span className="inline-flex rounded-full bg-zen-500 p-3 text-white">
          <CheckIcon className="h-6 w-6" />
        </span>
        <h2 className="mt-4 text-xl font-semibold text-brand-900">
          Thanks — we&apos;ll be in touch
        </h2>
        <p className="mx-auto mt-2 max-w-sm text-sm leading-6 text-brand-800/70">
          Someone from our team will reach out within one business day to set
          up your walkthrough.
        </p>
      </div>
    );
  }

  const inputCls =
    "w-full rounded-xl border border-mist-200 bg-white px-4 py-3 text-sm text-brand-900 placeholder:text-brand-800/40 outline-none transition focus:border-zen-500 focus:ring-2 focus:ring-zen-100";

  return (
    <form
      onSubmit={handleSubmit}
      className="space-y-5 rounded-3xl border border-mist-200 bg-white p-8 shadow-sm md:p-10"
    >
      <div className="grid gap-5 sm:grid-cols-2">
        <label className="block">
          <span className="mb-1.5 block text-sm font-medium text-brand-900">
            Full name
          </span>
          <input
            required
            name="name"
            autoComplete="name"
            placeholder="Your name"
            className={inputCls}
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-medium text-brand-900">
            Work email
          </span>
          <input
            required
            type="email"
            name="email"
            autoComplete="work email"
            placeholder="you@company.com"
            className={inputCls}
          />
        </label>
      </div>
      <div className="grid gap-5 sm:grid-cols-2">
        <label className="block">
          <span className="mb-1.5 block text-sm font-medium text-brand-900">
            Company
          </span>
          <input
            required
            name="company"
            autoComplete="organization"
            placeholder="Company name"
            className={inputCls}
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-medium text-brand-900">
            Company size
          </span>
          <select name="size" defaultValue="" required className={inputCls}>
            <option value="" disabled>
              Select a range
            </option>
            <option>Under 500</option>
            <option>500 – 2,000</option>
            <option>2,000 – 10,000</option>
            <option>10,000+</option>
          </select>
        </label>
      </div>
      <label className="block">
        <span className="mb-1.5 block text-sm font-medium text-brand-900">
          What would you like coaching to change?
        </span>
        <textarea
          name="message"
          rows={4}
          placeholder="e.g. New managers avoid difficult conversations; decisions stall between teams…"
          className={inputCls}
        />
      </label>
      {/* Honeypot: hidden from real users, bots fill it and get silently dropped */}
      <input
        type="text"
        name="website"
        tabIndex={-1}
        autoComplete="off"
        aria-hidden="true"
        className="hidden"
      />
      <button
        type="submit"
        disabled={status === "sending"}
        className="w-full rounded-full bg-brand-900 px-8 py-4 text-sm font-semibold text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {status === "sending" ? "Sending…" : "Request a demo"}
      </button>
      {status === "error" && (
        <p role="alert" className="text-center text-sm text-zen-700">
          {error} Please try again, or email us directly at{" "}
          <a href={`mailto:${site.email}`} className="font-semibold underline">
            {site.email}
          </a>
          .
        </p>
      )}
      <p className="text-center text-xs text-brand-800/50">
        We&apos;ll only use your details to arrange the walkthrough. No
        newsletters, no cold sequences.
      </p>
    </form>
  );
}
