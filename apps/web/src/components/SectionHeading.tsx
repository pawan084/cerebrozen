import Reveal from "@/components/Reveal";

export default function SectionHeading({
  eyebrow,
  title,
  lead,
  align = "center",
  dark = false,
}: {
  eyebrow?: string;
  title: string;
  lead?: string;
  align?: "center" | "left";
  dark?: boolean;
}) {
  const alignCls = align === "center" ? "mx-auto text-center" : "text-left";
  return (
    <Reveal className={`max-w-3xl ${alignCls}`}>
      {eyebrow && (
        <p
          className={`text-sm font-semibold uppercase tracking-widest ${
            dark ? "text-zen-400" : "text-zen-600"
          }`}
        >
          {eyebrow}
        </p>
      )}
      <h2
        className={`mt-3 text-3xl font-bold tracking-tight sm:text-4xl ${
          dark ? "text-white" : "text-brand-900"
        }`}
      >
        {title}
      </h2>
      {lead && (
        <p
          className={`mt-4 text-lg leading-relaxed ${
            dark ? "text-white/70" : "text-brand-800/70"
          }`}
        >
          {lead}
        </p>
      )}
    </Reveal>
  );
}
