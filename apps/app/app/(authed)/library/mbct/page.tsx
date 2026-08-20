"use client";

import { OfflineProgram } from "@/components/OfflineProgram";

// MBCT overview. Copy is the Android `ombct_*` strings verbatim, disclaimer
// included — "inspired by MBCT", never "MBCT", because the eight-week
// clinician-led programme is a specific thing and this is not it.
const MODULES = [
  {
    title: "Awareness and autopilot",
    body: "Mindfulness begins by noticing when attention has moved automatically.",
    practice: "Give full attention to one ordinary activity for two minutes.",
  },
  {
    title: "The body as an anchor",
    body: "Body sensations can provide present-moment information without needing analysis.",
    practice: "Complete the offline Body Scan once.",
  },
  {
    title: "Gathering attention",
    body: "Attention can be gently returned after wandering.",
    practice: "Notice five breaths and restart kindly whenever you lose count.",
  },
  {
    title: "Relating to thoughts",
    body: "Thoughts can be observed as mental events rather than facts.",
    practice: "Add “I am noticing the thought that…” before one recurring thought.",
  },
  {
    title: "Allowing experience",
    body: "Allowing means acknowledging what is present, not approving or giving up.",
    practice: "Name what is present and where you feel it in the body.",
  },
  {
    title: "Responding with care",
    body: "A deliberate response can replace an automatic reaction.",
    practice: "Pause and choose one supportive action you can realistically take.",
  },
  {
    title: "Caring for capacity",
    body: "Pleasant and mastery activities can support a balanced routine.",
    practice: "Schedule one nourishing and one manageable activity.",
  },
  {
    title: "Continuing practice",
    body: "A sustainable practice can be brief and flexible.",
    practice: "Choose when, where, and how long your next three practices will be.",
  },
];

export default function MbctOverview() {
  return (
    <OfflineProgram
      id="mbct"
      eyebrow="Offline mindfulness education"
      title="MBCT Overview"
      subtitle="Educational mindfulness practices inspired by MBCT. This is not a substitute for a clinician-led programme."
      modules={MODULES}
    />
  );
}
