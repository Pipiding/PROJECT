// Main application logic
document.addEventListener('DOMContentLoaded', function() {
    // Initialize the application
    initApp();
    
    // Add event listeners for navigation
    setupNavigation();
    
    // Set up user menu interactions
    setupUserMenu();
    
    // Prevent zooming on mobile devices
    setupZoomPrevention();
});

// Initialize the application
function initApp() {
    // Check if user is logged in
    const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
    
    if (isLoggedIn) {
        showMainApp();
        loadUserData();
    } else {
        showLoginScreen();
    }
}

// Show the main application screen
function showMainApp() {
    document.getElementById('login-screen').classList.add('hidden');
    document.getElementById('main-app').classList.remove('hidden');
    
    // Load initial data for the dashboard
    loadDashboardData();
}

// Show the login screen
function showLoginScreen() {
    document.getElementById('main-app').classList.add('hidden');
    document.getElementById('login-screen').classList.remove('hidden');
}

// Load user data
function loadUserData() {
    const username = localStorage.getItem('username');
    if (username) {
        document.getElementById('current-user').textContent = username;
    }
}

// Load dashboard data
function loadDashboardData() {
    // This would normally fetch data from the backend
    // Here we'll just use mock data for demonstration
    
    // Update last updated time
    document.getElementById('last-updated').textContent = new Date().toLocaleString();
    
    // Load recent transactions
    loadRecentTransactions();
}

// Load recent transactions for the dashboard
function loadRecentTransactions() {
    // This would normally fetch from the backend
    // Using mock data for demonstration
}

// Setup navigation between app sections
function setupNavigation() {
    // Get all navigation links
    const navLinks = document.querySelectorAll('.nav-link');
    
    // Add click event to each link
    navLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            
            // Remove active class from all links
            navLinks.forEach(navLink => {
                navLink.classList.remove('active');
                navLink.classList.add('border-transparent');
                navLink.classList.remove('border-blue-500');
                navLink.classList.remove('text-blue-600');
            });
            
            // Add active class to clicked link
            this.classList.add('active');
            this.classList.remove('border-transparent');
            this.classList.add('border-blue-500');
            this.classList.add('text-blue-600');
            
            // Hide all sections
            document.getElementById('dashboard-section').classList.add('hidden');
            document.getElementById('transactions-section').classList.add('hidden');
            document.getElementById('goals-section').classList.add('hidden');
            document.getElementById('reports-section').classList.add('hidden');
            
            // Show selected section based on href
            const sectionId = this.getAttribute('href').substring(1) + '-section';
            document.getElementById(sectionId).classList.remove('hidden');
        });
    });
    
    // Setup quick action buttons on dashboard
    document.getElementById('btn-add-transaction').addEventListener('click', function() {
        showAddTransactionModal();
    });
    
    document.getElementById('btn-import-csv').addEventListener('click', function() {
        showImportCSVModal();
    });
    
    document.getElementById('btn-savings-goal').addEventListener('click', function() {
        showAddGoalModal();
    });
    
    // Navigation buttons on sections
    document.getElementById('btn-add-transaction-page').addEventListener('click', function() {
        showAddTransactionModal();
    });
    
    document.getElementById('btn-import-csv-page').addEventListener('click', function() {
        showImportCSVModal();
    });
    
    document.getElementById('btn-add-goal').addEventListener('click', function() {
        showAddGoalModal();
    });
}

// Setup user menu interactions
function setupUserMenu() {
    const userMenuButton = document.getElementById('user-menu-button');
    const userDropdown = document.getElementById('user-dropdown');
    
    userMenuButton.addEventListener('click', function() {
        userDropdown.classList.toggle('hidden');
    });
    
    // Close the dropdown when clicking outside
    document.addEventListener('click', function(event) {
        if (!userMenuButton.contains(event.target) && !userDropdown.contains(event.target)) {
            userDropdown.classList.add('hidden');
        }
    });
    
    // Logout button
    document.getElementById('logout-button').addEventListener('click', function(e) {
        e.preventDefault();
        logout();
    });
}

// Prevent zooming on mobile devices
function setupZoomPrevention() {
    document.addEventListener('touchmove', function(event) {
        if (event.scale !== 1) { 
            event.preventDefault();
        }
    }, { passive: false });
}

// Show add transaction modal
function showAddTransactionModal() {
    document.getElementById('add-transaction-modal').classList.remove('hidden');
    
    // Set default date to today
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('transaction-date').value = today;
}

// Show import CSV modal
function showImportCSVModal() {
    document.getElementById('import-csv-modal').classList.remove('hidden');
}

// Show add goal modal
function showAddGoalModal() {
    document.getElementById('add-goal-modal').classList.remove('hidden');
    
    // Set default date to 3 months from now
    const threeMonthsFromNow = new Date();
    threeMonthsFromNow.setMonth(threeMonthsFromNow.getMonth() + 3);
    document.getElementById('goal-date').value = threeMonthsFromNow.toISOString().split('T')[0];
}

// Logout the user
function logout() {
    localStorage.removeItem('isLoggedIn');
    localStorage.removeItem('username');
    showLoginScreen();
}

// Show toast notification
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type} flex items-center`;
    
    let icon = '';
    switch(type) {
        case 'success':
            icon = '<i class="bx bx-check-circle text-green-500 text-xl mr-2"></i>';
            break;
        case 'error':
            icon = '<i class="bx bx-error-circle text-red-500 text-xl mr-2"></i>';
            break;
        case 'warning':
            icon = '<i class="bx bx-error text-yellow-500 text-xl mr-2"></i>';
            break;
        default:
            icon = '<i class="bx bx-info-circle text-blue-500 text-xl mr-2"></i>';
    }
    
    toast.innerHTML = `
        ${icon}
        <div>${message}</div>
    `;
    
    document.body.appendChild(toast);
    
    // Remove toast after 3 seconds
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%)';
        setTimeout(() => {
            document.body.removeChild(toast);
        }, 300);
    }, 3000);
}
