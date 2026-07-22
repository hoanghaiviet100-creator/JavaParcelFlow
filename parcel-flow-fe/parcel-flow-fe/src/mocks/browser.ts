import { setupWorker } from "msw/browser";
import { handlers } from "./handlers";

export const worker = setupWorker(...handlers);
export const initMocks = async () => {
  if (typeof window !== "undefined") {
    await worker.start({
      onUnhandledRequest: "bypass",
    });
  }
};
