document.addEventListener('DOMContentLoaded', function() {
    // Set up transaction modal events
    setupTransactionModal();
    
    // Set up transaction table filter events
    setupTransactionFilters();
    
    // Initialize transactions data
    initTransactionsData();
});

// Set up transaction modal events
function setupTransactionModal() {
    // Get modal elements
    const modal = document.getElementById('add-transaction-modal');
    const saveButton = document.getElementById('btn-save-transaction');
    const cancelButton = document.getElementById('btn-cancel-transaction');
    
    // Save transaction
    saveButton.addEventListener('click', function() {
        saveTransaction();
    });
    
    // Cancel and close modal
    cancelButton.addEventListener('click', function() {
        closeTransactionModal();
    });
    
    // Close modal when clicking outside
    modal.addEventListener('click', function(e) {
        if (e.target === modal) {
            closeTransactionModal();
        }
    });
}

// Close the transaction modal
function closeTransactionModal() {
    document.getElementById('add-transaction-modal').classList.add('hidden');
    resetTransactionForm();
}

// Reset the transaction form
function resetTransactionForm() {
    document.getElementById('transaction-date').value = new Date().toISOString().split('T')[0];
    document.getElementById('transaction-description').value = '';
    document.getElementById('transaction-amount').value = '';
    document.getElementById('transaction-type').value = 'expense';
    document.getElementById('transaction-category').value = 'food';
    document.getElementById('transaction-notes').value = '';
}

// Save a new transaction
function saveTransaction() {
    // Get form values
    const date = document.getElementById('transaction-date').value;
    const description = document.getElementById('transaction-description').value;
    const amount = parseFloat(document.getElementById('transaction-amount').value);
    const type = document.getElementById('transaction-type').value;
    const category = document.getElementById('transaction-category').value;
    const notes = document.getElementById('transaction-notes').value;
    
    // Validate the form
    if (!date || !description || isNaN(amount) || amount <= 0) {
        showToast('Please fill in all required fields with valid values.', 'error');
        return;
    }
    
    // Create transaction object
    const transaction = {
        id: generateId(),
        date: date,
        description: description,
        amount: type === 'expense' ? -amount : amount,
        category: category,
        notes: notes
    };
    
    // In a real app, this would call an API to save the transaction
    // For demo, we'll save to localStorage
    saveTransactionToStorage(transaction);
    
    // Close the modal
    closeTransactionModal();
    
    // Show success message
    showToast('Transaction saved successfully!', 'success');
    
    // Refresh transaction data
    loadTransactions();
    
    // If on dashboard, also refresh that data
    if (!document.getElementById('dashboard-section').classList.contains('hidden')) {
        loadDashboardData();
    }
}

// Generate a unique ID for the transaction
function generateId() {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
}

// Save transaction to localStorage
function saveTransactionToStorage(transaction) {
    let transactions = JSON.parse(localStorage.getItem('transactions')) || [];
    transactions.push(transaction);
    localStorage.setItem('transactions', JSON.stringify(transactions));
}

// Set up transaction filters
function setupTransactionFilters() {
    const applyFiltersButton = document.getElementById('btn-apply-filters');
    
    applyFiltersButton.addEventListener('click', function() {
        loadTransactions();
    });
}

// Initialize transactions data
function initTransactionsData() {
    // If no transactions exist in storage, create some sample data
    if (!localStorage.getItem('transactions')) {
        const sampleTransactions = [
            {
                id: 'sample1',
                date: '2023-04-15',
                description: 'Grocery Shopping',
                amount: -328.45,
                category: 'food',
                notes: 'Weekly groceries'
            },
            {
                id: 'sample2',
                date: '2023-04-14',
                description: 'Monthly Salary',
                amount: 8230.00,
                category: 'income',
                notes: 'April salary'
            },
            {
                id: 'sample3',
                date: '2023-04-13',
                description: 'Electric Bill',
                amount: -156.78,
                category: 'utilities',
                notes: 'Monthly bill'
            },
            {
                id: 'sample4',
                date: '2023-04-10',
                description: 'Transportation',
                amount: -45.00,
                category: 'transport',
                notes: 'Bus and subway'
            }
        ];
        
        localStorage.setItem('transactions', JSON.stringify(sampleTransactions));
    }
}

// Load transactions based on filters
function loadTransactions() {
    // Get filter values
    const categoryFilter = document.getElementById('category-filter').value;
    const dateFrom = document.getElementById('date-from').value;
    const dateTo = document.getElementById('date-to').value;
    const amountMin = document.getElementById('amount-min').value ? parseFloat(document.getElementById('amount-min').value) : null;
    const amountMax = document.getElementById('amount-max').value ? parseFloat(document.getElementById('amount-max').value) : null;
    
    // Get all transactions from storage
    let transactions = JSON.parse(localStorage.getItem('transactions')) || [];
    
    // Apply filters
    if (categoryFilter) {
        transactions = transactions.filter(t => t.category === categoryFilter);
    }
    
    if (dateFrom) {
        transactions = transactions.filter(t => t.date >= dateFrom);
    }
    
    if (dateTo) {
        transactions = transactions.filter(t => t.date <= dateTo);
    }
    
    if (amountMin !== null) {
        transactions = transactions.filter(t => Math.abs(t.amount) >= amountMin);
    }
    
    if (amountMax !== null) {
        transactions = transactions.filter(t => Math.abs(t.amount) <= amountMax);
    }
    
    // Sort by date (newest first)
    transactions.sort((a, b) => new Date(b.date) - new Date(a.date));
    
    // Update the UI
    updateTransactionsTable(transactions);
}

// Update the transactions table with filtered data
function updateTransactionsTable(transactions) {
    const tableBody = document.getElementById('transactions-list');
    const noTransactionsMessage = document.getElementById('no-transactions');
    
    // Clear the table
    tableBody.innerHTML = '';
    
    if (transactions.length === 0) {
        // Show no transactions message
        noTransactionsMessage.classList.remove('hidden');
    } else {
        // Hide no transactions message
        noTransactionsMessage.classList.add('hidden');
        
        // Add transactions to the table
        transactions.forEach(t => {
            const row = document.createElement('tr');
            
            // Format amount with sign and colors
            const formattedAmount = formatCurrency(t.amount);
            const amountClass = t.amount >= 0 ? 'text-green-600' : 'text-red-600';
            
            // Create category badge
            const categoryBadge = getCategoryBadge(t.category);
            
            row.innerHTML = `
                <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">${t.date}</td>
                <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">${t.description}</td>
                <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">${categoryBadge}</td>
                <td class="px-6 py-4 whitespace-nowrap text-sm ${amountClass}">${formattedAmount}</td>
                <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    <div class="flex space-x-2">
                        <button class="text-gray-400 hover:text-blue-500" onclick="editTransaction('${t.id}')">
                            <i class='bx bx-edit-alt'></i>
                        </button>
                        <button class="text-gray-400 hover:text-red-500" onclick="deleteTransaction('${t.id}')">
                            <i class='bx bx-trash'></i>
                        </button>
                    </div>
                </td>
            `;
            
            tableBody.appendChild(row);
        });
    }
}

// Format currency with ¥ symbol
function formatCurrency(amount) {
    const sign = amount >= 0 ? '+' : '';
    return `${sign}¥${Math.abs(amount).toFixed(2)}`;
}

// Get HTML for category badge
function getCategoryBadge(category) {
    let colorClass = '';
    let label = category.charAt(0).toUpperCase() + category.slice(1);
    
    switch (category) {
        case 'food':
            colorClass = 'bg-blue-100 text-blue-800';
            break;
        case 'transport':
            colorClass = 'bg-purple-100 text-purple-800';
            break;
        case 'utilities':
            colorClass = 'bg-yellow-100 text-yellow-800';
            break;
        case 'entertainment':
            colorClass = 'bg-pink-100 text-pink-800';
            break;
        case 'income':
            colorClass = 'bg-green-100 text-green-800';
            break;
        default:
            colorClass = 'bg-gray-100 text-gray-800';
    }
    
    return `<span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${colorClass}">${label}</span>`;
}

// Edit a transaction
function editTransaction(id) {
    // Get the transaction from storage
    const transactions = JSON.parse(localStorage.getItem('transactions')) || [];
    const transaction = transactions.find(t => t.id === id);
    
    if (!transaction) {
        showToast('Transaction not found.', 'error');
        return;
    }
    
    // Populate the form
    document.getElementById('transaction-date').value = transaction.date;
    document.getElementById('transaction-description').value = transaction.description;
    document.getElementById('transaction-amount').value = Math.abs(transaction.amount);
    document.getElementById('transaction-type').value = transaction.amount >= 0 ? 'income' : 'expense';
    document.getElementById('transaction-category').value = transaction.category;
    document.getElementById('transaction-notes').value = transaction.notes || '';
    
    // Show the modal
    document.getElementById('add-transaction-modal').classList.remove('hidden');
    
    // Change the save button to update
    const saveButton = document.getElementById('btn-save-transaction');
    saveButton.textContent = 'Update Transaction';
    
    // Store the transaction ID for updating
    saveButton.dataset.transactionId = id;
    
    // Override the save function for updating
    saveButton.onclick = function() {
        updateTransaction(id);
    };
}

// Update an existing transaction
function updateTransaction(id) {
    // Get form values
    const date = document.getElementById('transaction-date').value;
    const description = document.getElementById('transaction-description').value;
    const amount = parseFloat(document.getElementById('transaction-amount').value);
    const type = document.getElementById('transaction-type').value;
    const category = document.getElementById('transaction-category').value;
    const notes = document.getElementById('transaction-notes').value;
    
    // Validate the form
    if (!date || !description || isNaN(amount) || amount <= 0) {
        showToast('Please fill in all required fields with valid values.', 'error');
        return;
    }
    
    // Get all transactions
    let transactions = JSON.parse(localStorage.getItem('transactions')) || [];
    
    // Find and update the transaction
    const index = transactions.findIndex(t => t.id === id);
    
    if (index !== -1) {
        transactions[index] = {
            id: id,
            date: date,
            description: description,
            amount: type === 'expense' ? -amount : amount,
            category: category,
            notes: notes
        };
        
        // Save back to storage
        localStorage.setItem('transactions', JSON.stringify(transactions));
        
        // Show success message
        showToast('Transaction updated successfully!', 'success');
        
        // Reset the form and close the modal
        resetTransactionForm();
        document.getElementById('add-transaction-modal').classList.add('hidden');
        
        // Reset the save button
        const saveButton = document.getElementById('btn-save-transaction');
        saveButton.textContent = 'Save Transaction';
        saveButton.dataset.transactionId = '';
        saveButton.onclick = saveTransaction;
        
        // Refresh the data
        loadTransactions();
        
        // If on dashboard, also refresh that data
        if (!document.getElementById('dashboard-section').classList.contains('hidden')) {
            loadDashboardData();
        }
    } else {
        showToast('Transaction not found.', 'error');
    }
}

// Delete a transaction
function deleteTransaction(id) {
    if (confirm('Are you sure you want to delete this transaction?')) {
        // Get all transactions
        let transactions = JSON.parse(localStorage.getItem('transactions')) || [];
        
        // Filter out the transaction to delete
        transactions = transactions.filter(t => t.id !== id);
        
        // Save back to storage
        localStorage.setItem('transactions', JSON.stringify(transactions));
        
        // Show success message
        showToast('Transaction deleted successfully!', 'success');
        
        // Refresh the data
        loadTransactions();
        
        // If on dashboard, also refresh that data
        if (!document.getElementById('dashboard-section').classList.contains('hidden')) {
            loadDashboardData();
        }
    }
}
