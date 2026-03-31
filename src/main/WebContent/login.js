document.addEventListener("DOMContentLoaded", function () {

    // ── Elements ──────────────────────────────────────────────────────────────
    const form          = document.getElementById("loginForm");
    const emailInput    = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const togglePwd     = document.getElementById("togglePassword");
    const rememberBox   = document.getElementById("remember");
    const loginBtn      = document.getElementById("loginBtn");
    const btnText       = document.getElementById("btnText");
    const btnSpinner    = document.getElementById("btnSpinner");
    const alertBox      = document.getElementById("alertBox");

    // Role radio buttons
    const roleRadios    = document.querySelectorAll('input[name="role"]');

    // ── Check if all elements exist ───────────────────────────────────────────
    console.log("Email input:", emailInput);
    console.log("Password input:", passwordInput);
    console.log("Toggle button:", togglePwd);
    console.log("Remember box:", rememberBox);
    console.log("Login button:", loginBtn);
    console.log("Button text:", btnText);
    console.log("Button spinner:", btnSpinner);
    console.log("Alert box:", alertBox);
    console.log("Role radios:", roleRadios.length);

    // If any critical element is missing, stop execution
    if (!emailInput || !passwordInput || !form || !loginBtn || !btnText || !btnSpinner || !alertBox) {
        console.error("❌ Critical elements missing in HTML. Check IDs.");
        return;
    }

    // ── Restore remembered email ──────────────────────────────────────────────
    const savedEmail = localStorage.getItem("medcare_email");
    if (savedEmail) {
        emailInput.value    = savedEmail;
        rememberBox.checked = true;
        console.log("Restored email:", savedEmail);
    }

    // ── Toggle password visibility ────────────────────────────────────────────
    if (togglePwd) {
        togglePwd.addEventListener("click", function () {
            const isHidden = passwordInput.type === "password";
            passwordInput.type = isHidden ? "text" : "password";
            this.classList.toggle("fa-eye",       !isHidden);
            this.classList.toggle("fa-eye-slash",  isHidden);
        });
    } else {
        console.warn("Toggle password element not found.");
    }

    // ── Clear field errors on typing ──────────────────────────────────────────
    emailInput.addEventListener("input",    () => clearFieldError("emailGroup",    "emailError"));
    passwordInput.addEventListener("input", () => clearFieldError("passwordGroup", "passwordError"));

    // ── Form Submit ───────────────────────────────────────────────────────────
    form.addEventListener("submit", async function (e) {
        e.preventDefault();
        hideAlert();

        const email    = emailInput.value.trim();
        const password = passwordInput.value;       // preserve exact password

        // ── Get role safely (improved) ────────────────────────────────────────
        let role = '';
        const checkedRadio = document.querySelector('input[name="role"]:checked');
        if (checkedRadio) {
            role = checkedRadio.value;
            console.log("Selected role:", role);
        } else {
            console.warn("No role selected.");
        }

        if (!role) {
            showAlert("❌ Please select a role.", "error");
            return;
        }

        // ── Validate ──────────────────────────────────────────────────────────
        let valid = true;

        if (!email) {
            showFieldError("emailGroup", "emailError", "Email is required.");
            valid = false;
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            showFieldError("emailGroup", "emailError", "Please enter a valid email.");
            valid = false;
        }

        if (!password) {
            showFieldError("passwordGroup", "passwordError", "Password is required.");
            valid = false;
        }

        if (!valid) return;

        // ── Remember Me ───────────────────────────────────────────────────────
        if (rememberBox.checked) {
            localStorage.setItem("medcare_email", email);
            console.log("Email saved to localStorage");
        } else {
            localStorage.removeItem("medcare_email");
            console.log("Email removed from localStorage");
        }

        // ── Build Request ─────────────────────────────────────────────────────
        const formData = new URLSearchParams();
        formData.append("email",    email);
        formData.append("password", password);
        formData.append("role",     role);
        if (rememberBox.checked) formData.append("remember", "on");

        console.log("Sending data:", { email, password: "***", role, remember: rememberBox.checked });

        setLoading(true);

        // ── Send to Servlet ───────────────────────────────────────────────────
        try {
            const res = await fetch("LoginServlet", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: formData.toString()
            });

            console.log("Response status:", res.status);

            if (!res.ok) throw new Error("Server error: " + res.status);

            const data = await res.json();
            console.log("Response data:", data);

            if (data.success) {
                sessionStorage.setItem("medcare_user", JSON.stringify({
                    id:   data.userId,
                    name: data.name,
                    role: data.role
                }));

                showAlert("✅ Login successful! Redirecting...", "success");

                setTimeout(() => {
                    if      (data.role === "admin")      window.location.href = "admin-dashboard.html";
                    else if (data.role === "counsellor") window.location.href = "counsellor-dashboard.html";
                    else if (data.role === "patient")    window.location.href = "patient-dashboard.html";
                    else                                 window.location.href = "index.html";
                }, 900);

            } else {
                showAlert("❌ " + (data.message || "Login failed. Try again."), "error");
            }

        } catch (err) {
            console.error("Fetch error:", err);
            showAlert("⚠️ Network error. Please check your connection.", "error");
        } finally {
            setLoading(false);
        }
    });

    // ── Helper Functions ──────────────────────────────────────────────────────
    function setLoading(state) {
        loginBtn.disabled        = state;
        btnText.textContent      = state ? "Signing in..." : "Login";
        btnSpinner.style.display = state ? "inline-block" : "none";
    }

    function showAlert(msg, type) {
        alertBox.textContent   = msg;
        alertBox.className     = "alert " + (type === "success" ? "alert-success" : "alert-error");
        alertBox.style.display = "block";
        if (type === "error") setTimeout(hideAlert, 5000);
    }

    function hideAlert() {
        alertBox.style.display = "none";
        alertBox.textContent   = "";
    }

    function showFieldError(groupId, errorId, msg) {
        const group = document.getElementById(groupId);
        const errorSpan = document.getElementById(errorId);
        if (group) group.classList.add("has-error");
        if (errorSpan) errorSpan.textContent = msg;
    }

    function clearFieldError(groupId, errorId) {
        const group = document.getElementById(groupId);
        const errorSpan = document.getElementById(errorId);
        if (group) group.classList.remove("has-error");
        if (errorSpan) errorSpan.textContent = "";
    }
});