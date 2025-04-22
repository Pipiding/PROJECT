// Utility functions for the application

// Generate a unique ID
function generateId() {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
}

// Format currency with ¥ symbol
function formatCurrency(amount) {
    const sign = amount >= 0 ? '+' : '';
    return `${sign}¥${Math.abs(amount).toFixed(2)}`;
}

// Format date to locale string
function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString();
}

// Calculate days between two dates
function daysBetween(date1, date2) {
    const oneDay = 24 * 60 * 60 * 1000; // milliseconds in a day
    const firstDate = new Date(date1);
    const secondDate = new Date(date2);
    
    // Calculate the difference in days
    return Math.round(Math.abs((firstDate - secondDate) / oneDay));
}

// Calculate monthly savings rate
function calculateSavingsRate(income, expenses) {
    if (income <= 0) return 0;
    return Math.max(0, ((income - expenses) / income) * 100);
}

// Get color based on value range
function getStatusColor(value, thresholds) {
    if (value >= thresholds.excellent) return 'green';
    if (value >= thresholds.good) return 'blue';
    if (value >= thresholds.average) return 'yellow';
    return 'red';
}

// Group transactions by category
function groupByCategory(transactions) {
    const result = {};
    
    transactions.forEach(transaction => {
        const category = transaction.category;
        
        if (!result[category]) {
            result[category] = {
                total: 0,
                count: 0,
                transactions: []
            };
        }
        
        result[category].total += transaction.amount;
        result[category].count += 1;
        result[category].transactions.push(transaction);
    });
    
    return result;
}

// Group transactions by date
function groupByDate(transactions, grouping = 'month') {
    const result = {};
    
    transactions.forEach(transaction => {
        const date = new Date(transaction.date);
        let key;
        
        switch (grouping) {
            case 'day':
                key = transaction.date;
                break;
            case 'week':
                // Get the Monday of the week
                const day = date.getDay();
                const diff = date.getDate() - day + (day === 0 ? -6 : 1);
                const monday = new Date(date.setDate(diff));
                key = monday.toISOString().split('T')[0];
                break;
            case 'month':
                key = `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}`;
                break;
            case 'year':
                key = date.getFullYear().toString();
                break;
            default:
                key = transaction.date;
        }
        
        if (!result[key]) {
            result[key] = {
                total: 0,
                count: 0,
                transactions: []
            };
        }
        
        result[key].total += transaction.amount;
        result[key].count += 1;
        result[key].transactions.push(transaction);
    });
    
    return result;
}

// Filter transactions by date range
function filterByDateRange(transactions, startDate, endDate) {
    if (!startDate && !endDate) return transactions;
    
    return transactions.filter(transaction => {
        if (startDate && transaction.date < startDate) return false;
        if (endDate && transaction.date > endDate) return false;
        return true;
    });
}

// Calculate totals for a list of transactions
function calculateTotals(transactions) {
    let income = 0;
    let expenses = 0;
    
    transactions.forEach(transaction => {
        if (transaction.amount > 0) {
            income += transaction.amount;
        } else {
            expenses += Math.abs(transaction.amount);
        }
    });
    
    return {
        income,
        expenses,
        balance: income - expenses
    };
}

// Calculate goal progress
function calculateGoalProgress(goal) {
    const progress = Math.min(100, Math.round((goal.savedAmount / goal.targetAmount) * 100));
    
    let status = 'On Track';
    let statusColor = 'text-green-600';
    
    // If there's a target date, calculate if on track
    if (goal.targetDate) {
        const today = new Date();
        const targetDate = new Date(goal.targetDate);
        
        // If target date has passed
        if (today > targetDate) {
            if (progress >= 100) {
                status = 'Completed';
                statusColor = 'text-green-600';
            } else {
                status = 'Overdue';
                statusColor = 'text-red-600';
            }
        } else {
            // Calculate expected progress based on time elapsed
            const totalDays = daysBetween(goal.createdAt, goal.targetDate);
            const daysElapsed = daysBetween(goal.createdAt, today.toISOString());
            const expectedProgress = Math.round((daysElapsed / totalDays) * 100);
            
            if (progress >= 100) {
                status = 'Completed';
                statusColor = 'text-green-600';
            } else if (progress >= expectedProgress) {
                status = 'On Track';
                statusColor = 'text-green-600';
            } else if (progress >= expectedProgress * 0.8) {
                status = 'Slightly Behind';
                statusColor = 'text-yellow-600';
            } else {
                status = 'Behind';
                statusColor = 'text-red-600';
            }
        }
    } else {
        // No target date, just show progress
        if (progress >= 100) {
            status = 'Completed';
            statusColor = 'text-green-600';
        } else if (progress >= 75) {
            status = 'Good Progress';
            statusColor = 'text-green-600';
        } else if (progress >= 50) {
            status = 'Making Progress';
            statusColor = 'text-blue-600';
        } else if (progress >= 25) {
            status = 'Started';
            statusColor = 'text-blue-600';
        } else {
            status = 'Just Started';
            statusColor = 'text-gray-600';
        }
    }
    
    return {
        progress,
        status,
        statusColor
    };
}

// Generate random pastel color
function getRandomPastelColor() {
    const hue = Math.floor(Math.random() * 360);
    return `hsl(${hue}, 70%, 80%)`;
}

// Debounce function to limit how often a function can be called
function debounce(func, wait) {
    let timeout;
    return function(...args) {
        const context = this;
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(context, args), wait);
    };
}
