import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router-dom";
import { router } from "./router";
import { ThemeProvider } from "./theme";
import { ToastViewport } from "./components/toast/ToastViewport";
import { ConfirmHost } from "./components/confirm/ConfirmHost";
import "./index.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 10_000, retry: 1, refetchOnWindowFocus: false },
  },
});

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <ToastViewport />
        <ConfirmHost />
      </QueryClientProvider>
    </ThemeProvider>
  </React.StrictMode>,
);
