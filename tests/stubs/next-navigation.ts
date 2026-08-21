// Stand-in for `next/navigation` in component tests.
//
// ALIASED, not vi.mock'd. `next` is installed per app and not at the repo
// root, so `vi.mock("next/navigation", …)` cannot resolve the id from a test
// file and registers nothing — silently. The component then loads the REAL
// module, whose hooks read a router context that does not exist under
// @testing-library, and every render dies on "Cannot read properties of null
// (reading 'useContext')" with nothing pointing at the cause.
//
// A mutable holder rather than a mock function so a test can say where it is
// before rendering, and the value survives into the component's own render.

export const routerState = {
  pathname: "/",
  search: "",
  push: (_href: string) => {},
  replace: (_href: string) => {},
  back: () => {},
  refresh: () => {},
  prefetch: (_href: string) => {},
};

/** Reset between tests — the holder is module state and outlives a render. */
export function resetRouter() {
  routerState.pathname = "/";
  routerState.search = "";
}

export const usePathname = () => routerState.pathname;
export const useSearchParams = () => new URLSearchParams(routerState.search);
export const useRouter = () => ({
  push: routerState.push,
  replace: routerState.replace,
  back: routerState.back,
  refresh: routerState.refresh,
  prefetch: routerState.prefetch,
});
export const redirect = (href: string) => {
  routerState.pathname = href;
};
