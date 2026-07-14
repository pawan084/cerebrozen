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

  useEffect(() => {
    if (paused) return;
    const id = setInterval(() => {
      setIndex((i) => (i + 1) % items.length);
    }, 6000);
    return () => clearInterval(id);
  }, [paused, items.length]);

  const current = items[index];

  return (
    <div
      className="mx-auto max-w-3xl"
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

      <div className="mt-10 flex justify-center gap-2.5">
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
