from playwright.sync_api import sync_playwright

def verify_lane_view():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(ignore_https_errors=True)
        page = context.new_page()

        try:
            print("Navigating to dashboard...")
            page.goto("http://localhost:8282/")
            page.wait_for_timeout(3000)

            # Click on Lane 1
            print("Clicking Lane 1...")
            lane1_link = page.locator("text=lane-1")
            if lane1_link.count() > 0:
                lane1_link.first.click()
            else:
                print("Could not find lane-1 text, trying generic row click")
                page.mouse.click(200, 200) # Fallback click

            print("Waiting on Lane View...")
            page.wait_for_timeout(5000)

            print("Taking screenshot...")
            page.screenshot(path="verification.png", full_page=True)
            print("Done.")

        except Exception as e:
            print(f"Error: {e}")
            page.screenshot(path="verification_error.png")
        finally:
            browser.close()

if __name__ == "__main__":
    verify_lane_view()
