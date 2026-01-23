// API Base URL
const API_BASE = '/api/auth';

// ==================== TOKEN & SESSION HELPERS ====================

// Store minimal session info (tokens live in HttpOnly cookies set by the server)
function storeSessionInfo(data) {
    localStorage.setItem('userId', data.userId);
    localStorage.setItem('username', data.username);
}

// Clear stored session info
function clearAuth() {
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
}

// Read CSRF token from Spring's XSRF-TOKEN cookie
function getCsrfToken() {
    const match = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));
    return match ? decodeURIComponent(match.split('=')[1]) : null;
}

// Toggle nav/login/logout UI depending on session
function updateNavState() {
    const currentUser = getCurrentUser();
    const isLoggedIn = Boolean(currentUser);

    document.querySelectorAll('[data-nav="welcome"]').forEach((el) => {
        if (isLoggedIn) {
            el.textContent = `Hi, ${currentUser.username}`;
            el.classList.remove('hidden');
        } else {
            el.textContent = '';
            el.classList.add('hidden');
        }
    });

    document.querySelectorAll('[data-nav="login"]').forEach((el) => {
        el.classList.toggle('hidden', isLoggedIn);
    });

    document.querySelectorAll('[data-nav="register"]').forEach((el) => {
        el.classList.toggle('hidden', isLoggedIn);
    });

    document.querySelectorAll('[data-nav="items"]').forEach((el) => {
        el.classList.toggle('hidden', !isLoggedIn);
    });

    document.querySelectorAll('[data-nav="logout"]').forEach((el) => {
        el.classList.toggle('hidden', !isLoggedIn);
    });
}

function wireLogoutButtons() {
    document.querySelectorAll('[data-action="logout"]').forEach((btn) => {
        btn.addEventListener('click', async (e) => {
            e.preventDefault();
            await logout();
        });
    });
}

// Fetch helper that sends cookies + CSRF header; server reads tokens from HttpOnly cookies
async function fetchWithAuth(url, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        ...options.headers,
    };
    const csrf = getCsrfToken();
    if (csrf) {
        headers['X-XSRF-TOKEN'] = csrf;
    }

    const response = await fetch(url, {
        ...options,
        headers,
        credentials: 'include',
    });

    if (response.status === 401) {
        clearAuth();
        window.location.href = 'login.html';
        throw new Error('Session expired. Please login again.');
    }

    return response;
}

// ==================== AVAILABILITY CHECKS ====================

async function checkUsernameAvailability(username) {
    if (!username || username.length < 3) return null;
    
    try {
        const response = await fetch(`${API_BASE}/check-username/${encodeURIComponent(username)}`, {
            credentials: 'include',
        });
        return await response.json();
    } catch (error) {
        console.error('Username check error:', error);
        return null;
    }
}

async function checkEmailAvailability(email) {
    if (!email || !email.includes('@')) return null;
    
    try {
        const response = await fetch(`${API_BASE}/check-email/${encodeURIComponent(email)}`, {
            credentials: 'include',
        });
        return await response.json();
    } catch (error) {
        console.error('Email check error:', error);
        return null;
    }
}

// Debounce helper - wait until user stops typing
function debounce(func, wait) {
    let timeout;
    return function (...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
    };
}

// ==================== UI HELPERS ====================

function togglePassword(inputId) {
    const input = document.getElementById(inputId);
    const icon = input.parentElement.querySelector('i');
    
    if (input.type === 'password') {
        input.type = 'text';
        icon.classList.remove('fa-eye');
        icon.classList.add('fa-eye-slash');
    } else {
        input.type = 'password';
        icon.classList.remove('fa-eye-slash');
        icon.classList.add('fa-eye');
    }
}

function showAlert(message, type = 'error') {
    const alert = document.getElementById('alert');
    const alertMessage = document.getElementById('alert-message');
    if (!alert || !alertMessage) return false;
    
    alert.className = `alert alert-${type} show`;
    alertMessage.textContent = message;
    
    setTimeout(() => {
        alert.classList.remove('show');
    }, 5000);

    return true;
}

function setLoading(loading) {
    const btn = document.getElementById('submitBtn');
    const btnText = document.getElementById('btnText');
    const btnSpinner = document.getElementById('btnSpinner');
    
    btn.disabled = loading;
    btnText.style.display = loading ? 'none' : 'inline';
    btnSpinner.style.display = loading ? 'block' : 'none';
}

function setFieldStatus(el, status, message) {
    if (!el) return;

    el.textContent = message || '';
    el.classList.remove('status-loading', 'status-available', 'status-unavailable', 'status-info', 'hidden');

    if (!status) {
        if (!message) {
            el.classList.add('hidden');
        } else {
            el.classList.remove('hidden');
            el.classList.add('status-info');
        }
        return;
    }

    el.classList.add(`status-${status}`);
}

// ==================== AUTH FLOWS ====================

async function login(credentials) {
    setLoading(true);
    
    try {
        const csrf = getCsrfToken();
        const response = await fetch(`${API_BASE}/login`, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
            },
            body: JSON.stringify(credentials),
        });

        const data = await response.json();

        if (response.ok) {
            storeSessionInfo(data);
            updateNavState();
            showAlert('Login successful! Redirecting...', 'success');
            
            setTimeout(() => {
                window.location.href = 'items.html';
            }, 1200);
        } else {
            const errorMessage = data.message || data.error || 'Invalid email or password';
            showAlert(errorMessage, 'error');
        }
    } catch (error) {
        console.error('Login error:', error);
        showAlert('An error occurred. Please try again.', 'error');
    } finally {
        setLoading(false);
    }
}

async function register(userData) {
    setLoading(true);
    
    try {
        const csrf = getCsrfToken();
        const response = await fetch(`${API_BASE}/register`, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
            },
            body: JSON.stringify(userData),
        });

        const data = await response.json();

        if (response.ok) {
            storeSessionInfo(data);
            updateNavState();
            showAlert('Account created successfully! Redirecting...', 'success');
            
            setTimeout(() => {
                window.location.href = 'index.html';
            }, 1200);
        } else {
            if (data.errors && Array.isArray(data.errors)) {
                showAlert(data.errors.join(', '), 'error');
            } else {
                const errorMessage = data.message || data.error || 'Registration failed. Please try again.';
                showAlert(errorMessage, 'error');
            }
        }
    } catch (error) {
        console.error('Registration error:', error);
        showAlert('An error occurred. Please try again.', 'error');
    } finally {
        setLoading(false);
    }
}

function getCurrentUser() {
    if (!localStorage.getItem('userId')) return null;
    return {
        userId: localStorage.getItem('userId'),
        username: localStorage.getItem('username'),
    };
}

async function logout() {
    try {
        const csrf = getCsrfToken();
        const headers = {
            'Content-Type': 'application/json',
            ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
        };

        const res = await fetch(`${API_BASE}/logout`, {
            method: 'POST',
            headers,
            credentials: 'include',
        });

        if (!res.ok) {
            throw new Error(`Logout failed with status ${res.status}`);
        }

        const shown = showAlert('Logout successful. Tokens revoked.', 'success');
        if (!shown) {
            alert('Logout successful. Tokens revoked.');
        }
    } catch (error) {
        console.error('Logout error:', error);
        alert('Logout failed, but your local session was cleared.');
    } finally {
        clearAuth();
        updateNavState();
        window.location.href = 'login.html';
    }
}

// ==================== PAGE INITIALIZERS ====================

function initLoginForm() {
    const form = document.getElementById('loginForm');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const email = document.getElementById('email')?.value.trim();
        const password = document.getElementById('password')?.value;

        if (!email || !password) {
            showAlert('Please fill in all fields', 'error');
            return;
        }

        await login({ email, password });
    });
}

function initRegisterForm() {
    const form = document.getElementById('registerForm');
    if (!form) return;

    const birthDateInput = document.getElementById('birthDate');
    if (birthDateInput) {
        const today = new Date();
        const minAge = new Date(today.getFullYear() - 18, today.getMonth(), today.getDate());
        birthDateInput.max = minAge.toISOString().split('T')[0];
    }

    const usernameInput = document.getElementById('username');
    const usernameStatus = document.getElementById('usernameStatus');
    let usernameRequest = 0;
    if (usernameInput && usernameStatus) {
        const checkUsername = debounce(async (value) => {
            const current = ++usernameRequest;
            if (!value || value.length < 3) {
                setFieldStatus(usernameStatus, null, 'Enter at least 3 characters');
                return;
            }
            setFieldStatus(usernameStatus, 'loading', 'Checking availability...');
            const available = await checkUsernameAvailability(value);
            if (current !== usernameRequest) return;
            if (available === null) {
                setFieldStatus(usernameStatus, 'unavailable', 'Unable to check right now');
                return;
            }
            setFieldStatus(
                usernameStatus,
                available ? 'available' : 'unavailable',
                available ? 'Username is available' : 'Username is already taken'
            );
        }, 350);

        usernameInput.addEventListener('input', (e) => {
            checkUsername(e.target.value.trim());
        });
    }

    const emailInput = document.getElementById('email');
    const emailStatus = document.getElementById('emailStatus');
    let emailRequest = 0;
    if (emailInput && emailStatus) {
        const checkEmail = debounce(async (value) => {
            const current = ++emailRequest;
            if (!value || !value.includes('@')) {
                setFieldStatus(emailStatus, null, 'Enter a valid email');
                return;
            }
            setFieldStatus(emailStatus, 'loading', 'Checking email...');
            const available = await checkEmailAvailability(value);
            if (current !== emailRequest) return;
            if (available === null) {
                setFieldStatus(emailStatus, 'unavailable', 'Unable to check right now');
                return;
            }
            setFieldStatus(
                emailStatus,
                available ? 'available' : 'unavailable',
                available ? 'Email is available' : 'Email already in use'
            );
        }, 350);

        emailInput.addEventListener('input', (e) => {
            checkEmail(e.target.value.trim());
        });
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const formData = {
            firstName: document.getElementById('firstName')?.value.trim(),
            lastName: document.getElementById('lastName')?.value.trim(),
            username: document.getElementById('username')?.value.trim(),
            email: document.getElementById('email')?.value.trim(),
            birthDate: document.getElementById('birthDate')?.value,
            role: document.getElementById('role')?.value,
            password: document.getElementById('password')?.value,
            confirmPassword: document.getElementById('confirmPassword')?.value
        };

        if (formData.password !== formData.confirmPassword) {
            showAlert('Passwords do not match', 'error');
            return;
        }

        const passwordRegex = /^(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*]).{6,}$/;
        if (!passwordRegex.test(formData.password)) {
            showAlert('Password must contain at least one uppercase letter, one digit, and one special character (!@#$%^&*)', 'error');
            return;
        }

        await register(formData);
    });
}

function initAuthUI() {
    updateNavState();
    wireLogoutButtons();
}

document.addEventListener('DOMContentLoaded', () => {
    initAuthUI();
    initLoginForm();
    initRegisterForm();
});
