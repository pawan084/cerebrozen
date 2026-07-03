"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { hasSession } from "@/lib/api";

export default function Index() {
  const router = useRouter();
  useEffect(() => {
    router.replace(hasSession() ? "/home" : "/signin");
  }, [router]);
  return null;
}
