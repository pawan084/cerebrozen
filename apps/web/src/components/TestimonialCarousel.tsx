"use client";

import Image from "next/image";
import { useEffect, useState } from "react";

export type Testimonial = {
  quote: string;
  name: string;
  role: string;
  org: string;
  photo: string;
};

export default function TestimonialCarousel({
  items,
}: {
  items: Testimonial[];
}) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const [reduced, setReduced] = useState(false);

  // No auto-advance for users who asked for reduced motion (WCAG 2.3 / 2.2.2).
  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const sync = () => setReduced(mq.matches);
    sync();
    mq.addEventListener("change", sync);
    return () => mq.removeEventListener("change", sync);
  }, []);

  const autoplay = !paused && !reduced;
  useEffect(() => {
    if (!autoplay) return;
    const id = setInterval(() => {
      setIndex((i) => (i + 1) % items.length);
    }, 6000);
    return () => clearInterval(id);
  }, [autoplay, items.length]);

  const current = items[index];

  return (
    <div
      className="mx-auto max-w-3xl"
      role="group"
      aria-roledescription="carousel"
      aria-label="Customer testimonials"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      <p
        className="text-center font-[family-name:var(--font-heading)] text-4xl font-bold leading-none text-brand-900"
        aria-hidden
      >
        “
      </p>
      <div
        key={index}
        role="group"
        aria-roledescription="slide"
        aria-label={`${index + 1} of ${items.length}`}
        aria-live={paused ? "polite" : "off"}
        className="mt-2 grid items-center gap-8 sm:grid-cols-[auto_1fr]"
        style={{ animation: "fadeSlide 0.5s ease both" }}
      >
        <span className="relative mx-auto block h-32 w-32 overflow-hidden rounded-full sm:h-36 sm:w-36">
          <Image
            src={current.photo}
            alt={current.name}
            fill
            className="object-cover"
            sizes="144px"
          />
        </span>
        <div className="min-h-36 text-center sm:text-left">
          <blockquote className="leading-relaxed text-brand-900">
            {current.quote}
          </blockquote>
          <p className="mt-5 font-bold text-brand-900">{current.name}</p>
          <p className="mt-1 text-brand-800">
            {current.role}, {current.org}
          </p>
        </div>
      </div>

      <div className="mt-10 flex items-center justify-center gap-2.5">
        {!reduced && (
          <button
            type="button"
            onClick={() => setPaused((p) => !p)}
            aria-label={paused ? "Play testimonials" : "Pause testimonials"}
            className="mr-2 flex h-6 w-6 items-center justify-center rounded-full text-brand-500 transition hover:text-zen-600"
          >
            {paused ? (
              <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="currentColor" aria-hidden="true">
                <path d="M4 3l9 5-9 5V3z" />
              </svg>
            ) : (
              <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="currentColor" aria-hidden="true">
                <rect x="4" y="3" width="3" height="10" rx="1" />
                <rect x="9" y="3" width="3" height="10" rx="1" />
              </svg>
            )}
          </button>
        )}
        {items.map((_, i) => (
          <button
            key={i}
            type="button"
            aria-label={`Show testimonial ${i + 1}`}
            aria-current={i === index ? "true" : undefined}
            onClick={() => setIndex(i)}
            className={`h-2.5 w-2.5 rounded-full transition ${
              i === index ? "bg-zen-500" : "bg-brand-100 hover:bg-zen-400"
            }`}
          />
        ))}
      </div>

      <style>{`
        @keyframes fadeSlide {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: none; }
        }
      `}</style>
    </div>
  );
}
