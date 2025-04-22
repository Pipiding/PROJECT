document.addEventListener('DOMContentLoaded', function() {
    // Set up login form submission
    setupLoginForm();
});

// Set up login form submission
function setupLoginForm() {
    const loginForm = document.getElementById('login-form');
    
    loginForm.addEventListener('submit', function(e) {
        e.preventDefault();
        
        // Get form data
        const username = document.getElementById('username').value;
        const password = document.getElementById('password').value;
        const rememberMe = document.getElementById('remember-me').checked;
        
        // Validate the form
        if (!username || !password) {
            showLoginError('Please enter both username and password.');
            return;
        }
        
        // Attempt login (in a real app, this would call an API)
        login(username, password, rememberMe);
    });
}

// Show login error
function showLoginError(message) {
    const errorElement = document.createElement('div');
    errorElement.className = 'text-red-600 text-sm mt-2';
    errorElement.textContent = message;
    
    // Remove any existing error messages
    const existingError = document.querySelector('.text-red-600');
    if (existingError) {
        existingError.remove();
    }
    
    // Insert the error message after the login button
    const loginButton = document.querySelector('button[type="submit"]');
    loginButton.parentNode.insertBefore(errorElement, loginButton.nextSibling);
}

// Login the user
function login(username, password, rememberMe) {
    // In a real application, this would call an API to verify credentials
    // For this demo, we'll accept any username/password with basic validation
    
    // Add simple validation for demo purposes
    if (password.length < 4) {
        showLoginError('Password must be at least 4 characters long.');
        return;
    }
    
    // Store login state
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('username', username);
    
    if (rememberMe) {
        // In a real app, you would use a more secure method than localStorage
        localStorage.setItem('rememberMe', 'true');
    }
    
    // Show the main application
    showMainApp();
    
    // Update the username in the UI
    document.getElementById('current-user').textContent = username;
    
    // Show success message
    showToast('Login successful. Welcome back!', 'success');
}

// Show the main application
function showMainApp() {
    document.getElementById('login-screen').classList.add('hidden');
    document.getElementById('main-app').classList.remove('hidden');
    
    // Load initial data for the dashboard
    loadDashboardData();
}
