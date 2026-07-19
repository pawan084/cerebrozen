import type { MetadataRoute } from "next";

/** Makes the coaching app installable (PWA). Icons reference the generated
 *  app/icon.svg. Kept minimal + private — no deep-linkable share targets. */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "CereBroZen",
    short_name: "CereBroZen",
    description: "Your always-on performance coach.",
    start_url: "/",
    display: "standalone",
    background_color: "#0a0a0a",
    theme_color: "#f56b6b",
    icons: [
      { src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" },
      { src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "maskable" },
    ],
  };
}
