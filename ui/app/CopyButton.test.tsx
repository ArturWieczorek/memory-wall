import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
import { CopyButton } from "./CopyButton";

afterEach(cleanup);

describe("CopyButton", () => {
  it("copies the value to the clipboard and flashes 'copied'", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    render(<CopyButton value="deadbeef" label="copy tx" />);
    fireEvent.click(screen.getByRole("button"));

    expect(writeText).toHaveBeenCalledWith("deadbeef");
    expect(await screen.findByText("copied")).toBeInTheDocument();
  });
});
