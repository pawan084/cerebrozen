"use client";

import { OfflineProgram } from "@/components/OfflineProgram";

// CBT-I overview. Copy is the Android `ocbti_*` strings verbatim — same
// product, same words, and the disclaimer travels with them rather than being
// re-worded per client.
const MODULES = [
  {
    title: "Understanding sleep",
    body: "Sleep pressure and the body clock both influence when sleep feels possible.",
    practice: "For one week, note bedtime, wake time, and how rested you feel.",
  },
  {
    title: "A consistent wake time",
    body: "A regular morning anchor can help make the daily rhythm more predictable.",
    practice: "Choose a realistic wake-time range for the next few days.",
  },
  {
    title: "The bed-sleep connection",
    body: "Quiet, repeatable cues can help the bed become associated with winding down.",
    practice: "Create a short pre-bed routine using two calm, low-light activities.",
  },
  {
    title: "Light and activity",
    body: "Daytime light and movement provide timing cues to the body clock.",
    practice: "Notice when you receive daylight and when activity feels sustainable.",
  },
  {
    title: "Thoughts at night",
    body: "Trying to force sleep can add effort and frustration.",
    practice: "Write tomorrow’s tasks down earlier, then set the list aside.",
  },
  {
    title: "Review and seek support",
    body: "Persistent sleep difficulty can have many causes and deserves individual assessment.",
    practice: "Review patterns and consider discussing concerns with a qualified professional.",
  },
];

export default function CbtIOverview() {
  return (
    <OfflineProgram
      id="cbti"
      eyebrow="Offline sleep education"
      title="CBT-I Overview"
      subtitle="Educational modules about habits that can support sleep. Work with a qualified clinician for personalised CBT-I."
      modules={MODULES}
    />
  );
}
