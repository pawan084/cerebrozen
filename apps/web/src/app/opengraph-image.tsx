import { ImageResponse } from "next/og";
import { site } from "@/lib/site";

export const alt = `${site.name} — ${site.tagline}`;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

/**
 * Generated share card — replaces the 646 KB static hero.jpg for OG/Twitter.
 * Brand cream-and-coral, system fonts (self-contained; satori needs flex on every
 * element that has more than one child).
 */
export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          background: "linear-gradient(135deg, #faf6ef 0%, #f3e7d7 100%)",
          padding: "72px 80px",
          fontFamily: "sans-serif",
          color: "#101010",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", fontSize: 36, fontWeight: 700 }}>
          <span>CereBr</span>
          <span style={{ color: "#f56b6b" }}>o</span>
          <span>Zen</span>
        </div>

        <div style={{ display: "flex", flexDirection: "column" }}>
          <div
            style={{
              display: "flex",
              fontSize: 66,
              fontWeight: 800,
              lineHeight: 1.05,
              letterSpacing: "-0.02em",
              maxWidth: 940,
            }}
          >
            Every employee, coached in the moments that matter.
          </div>
          <div style={{ display: "flex", fontSize: 30, color: "#4a4a4a", marginTop: 26 }}>
            AI performance coaching that turns hesitation into measurable results.
          </div>
        </div>

        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            fontSize: 24,
            color: "#6b6b6b",
          }}
        >
          <div style={{ display: "flex", alignItems: "center" }}>
            <div style={{ width: 12, height: 12, borderRadius: 6, background: "#f56b6b", marginRight: 12 }} />
            {site.domain}
          </div>
          <div style={{ display: "flex" }}>Enterprise AI coaching</div>
        </div>
      </div>
    ),
    { ...size },
  );
}
