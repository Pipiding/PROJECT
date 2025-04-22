document.addEventListener('DOMContentLoaded', function() {
    // Set up goals modal events
    setupGoalsModal();
    
    // Initialize goals data
    initGoalsData();
    
    // Load goals
    loadGoals();
});

// Set up goals modal events
function setupGoalsModal() {
    // Get modal elements
    const modal = document.getElementById('add-goal-modal');
    const saveButton = document.getElementById('btn-save-goal');
    const cancelButton = document.getElementById('btn-cancel-goal');
    
    // Save goal
    saveButton.addEventListener('click', function() {
        saveGoal();
    });
    
    // Cancel and close modal
    cancelButton.addEventListener('click', function() {
        closeGoalModal();
    });
    
    // Close modal when clicking outside
    modal.addEventListener('click', function(e) {
        if (e.target === modal) {
            closeGoalModal();
        }
    });
}

// Close the goal modal
function closeGoalModal() {
    document.getElementById('add-goal-modal').classList.add('hidden');
    resetGoalForm();
}

// Reset the goal form
function resetGoalForm() {
    const threeMonthsFromNow = new Date();
    threeMonthsFromNow.setMonth(threeMonthsFromNow.getMonth() + 3);
    
    document.getElementById('goal-name').value = '';
    document.getElementById('goal-amount').value = '';
    document.getElementById('goal-date').value = threeMonthsFromNow.toISOString().split('T')[0];
    document.getElementById('goal-initial').value = '';
    document.getElementById('goal-notes').value = '';
}

// Save a new goal
function saveGoal() {
    // Get form values
    const name = document.getElementById('goal-name').value;
    const amount = parseFloat(document.getElementById('goal-amount').value);
    const date = document.getElementById('goal-date').value;
    const initial = parseFloat(document.getElementById('goal-initial').value) || 0;
    const notes = document.getElementById('goal-notes').value;
    
    // Validate the form
    if (!name || isNaN(amount) || amount <= 0) {
        showToast('Please fill in all required fields with valid values.', 'error');
        return;
    }
    
    // Validate initial contribution
    if (initial > amount) {
        showToast('Initial contribution cannot be greater than the target amount.', 'error');
        return;
    }
    
    // Create goal object
    const goal = {
        id: generateId(),
        name: name,
        targetAmount: amount,
        targetDate: date || null,
        savedAmount: initial,
        notes: notes,
        createdAt: new Date().toISOString()
    };
    
    // In a real app, this would call an API to save the goal
    // For demo, we'll save to localStorage
    saveGoalToStorage(goal);
    
    // Close the modal
    closeGoalModal();
    
    // Show success message
    showToast('Savings goal created successfully!', 'success');
    
    // Refresh goals data
    loadGoals();
}

// Save goal to localStorage
function saveGoalToStorage(goal) {
    let goals = JSON.parse(localStorage.getItem('goals')) || [];
    goals.push(goal);
    localStorage.setItem('goals', JSON.stringify(goals));
}

// Initialize goals data
function initGoalsData() {
    // If no goals exist in storage, create some sample data
    if (!localStorage.getItem('goals')) {
        const sampleGoals = [
            {
                id: 'goal1',
                name: 'New Laptop',
                targetAmount: 10000,
                targetDate: '2023-12-31',
                savedAmount: 6500,
                notes: 'For work and personal projects',
                createdAt: '2023-01-15T00:00:00.000Z'
            },
            {
                id: 'goal2',
                name: 'Vacation Trip',
                targetAmount: 10000,
                targetDate: '2023-07-15',
                savedAmount: 3000,
                notes: 'Summer vacation to the beach',
                createdAt: '2023-02-10T00:00:00.000Z'
            },
            {
                id: 'goal3',
                name: 'Emergency Fund',
                targetAmount: 50000,
                targetDate: null,
                savedAmount: 42500,
                notes: '6 months of living expenses',
                createdAt: '2022-09-01T00:00:00.000Z'
            }
        ];
        
        localStorage.setItem('goals', JSON.stringify(sampleGoals));
    }
}

// Load all goals
function loadGoals() {
    // Get all goals from storage
    const goals = JSON.parse(localStorage.getItem('goals')) || [];
    
    // Sort by created date (newest first)
    goals.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    
    // Update the UI
    updateGoalsList(goals);
}

// Update the goals list in the UI
function updateGoalsList(goals) {
    const goalsSection = document.getElementById('goals-section');
    const goalsContainer = goalsSection.querySelector('.grid');
    
    // Clear the container
    goalsContainer.innerHTML = '';
    
    if (goals.length === 0) {
        // Show no goals message
        const noGoalsMessage = document.createElement('div');
        noGoalsMessage.className = 'col-span-full text-center py-10';
        noGoalsMessage.innerHTML = `
            <p class="text-gray-500">You haven't created any savings goals yet.</p>
            <button id="btn-create-first-goal" class="mt-4 inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500">
                <i class='bx bx-plus mr-2'></i> Create Your First Goal
            </button>
        `;
        goalsContainer.appendChild(noGoalsMessage);
        
        // Add event listener to the button
        document.getElementById('btn-create-first-goal').addEventListener('click', function() {
            showAddGoalModal();
        });
    } else {
        // Add goals to the container
        goals.forEach(goal => {
            // Calculate progress percentage
            const progress = Math.min(100, Math.round((goal.savedAmount / goal.targetAmount) * 100));
            
            // Format date
            let formattedDate = 'No Target Date';
            if (goal.targetDate) {
                const date = new Date(goal.targetDate);
                formattedDate = new Intl.DateTimeFormat('en-US', { 
                    month: 'short', 
                    day: 'numeric', 
                    year: 'numeric' 
                }).format(date);
            }
            
            // Determine color based on progress
            let progressColor = 'bg-blue-600';
            if (progress >= 75) progressColor = 'bg-green-600';
            else if (progress >= 50) progressColor = 'bg-blue-600';
            else if (progress >= 25) progressColor = 'bg-purple-600';
            else progressColor = 'bg-red-600';
            
            const goalElement = document.createElement('div');
            goalElement.className = 'bg-white rounded-lg shadow-sm border border-gray-100 p-6 hover:shadow-md transition-shadow';
            goalElement.innerHTML = `
                <div class="flex justify-between items-start">
                    <div>
                        <h3 class="text-lg font-medium text-gray-900">${goal.name}</h3>
                        <p class="text-sm text-gray-500 mt-1">Target Date: ${formattedDate}</p>
                    </div>
                    <div class="flex space-x-2">
                        <button class="text-gray-400 hover:text-gray-500" onclick="editGoal('${goal.id}')">
                            <i class='bx bx-edit-alt'></i>
                        </button>
                        <button class="text-gray-400 hover:text-red-500" onclick="deleteGoal('${goal.id}')">
                            <i class='bx bx-trash'></i>
                        </button>
                    </div>
                </div>
                
                <div class="mt-4">
                    <div class="flex justify-between items-center mb-1">
                        <span class="text-sm font-medium text-gray-700">Progress</span>
                        <span class="text-sm font-medium text-gray-700">${progress}%</span>
                    </div>
                    <div class="w-full bg-gray-200 rounded-full h-2.5">
                        <div class="${progressColor} h-2.5 rounded-full" style="width: ${progress}%"></div>
                    </div>
                </div>
                
                <div class="mt-4 flex justify-between items-center">
                    <div>
                        <p class="text-sm text-gray-500">Saved</p>
                        <p class="text-lg font-semibold text-gray-900">¥${goal.savedAmount.toFixed(0)}</p>
                    </div>
                    <div>
                        <p class="text-sm text-gray-500">Target</p>
                        <p class="text-lg font-semibold text-gray-900">¥${goal.targetAmount.toFixed(0)}</p>
                    </div>
                    <div>
                        <p class="text-sm text-gray-500">Remaining</p>
                        <p class="text-lg font-semibold text-gray-900">¥${(goal.targetAmount - goal.savedAmount).toFixed(0)}</p>
                    </div>
                </div>
                
                <div class="mt-6">
                    <button class="w-full flex justify-center items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500" onclick="addFundsToGoal('${goal.id}')">
                        <i class='bx bx-plus-circle mr-2'></i> Add Funds
                    </button>
                </div>
            `;
            
            goalsContainer.appendChild(goalElement);
        });
    }
}

// Edit a goal
function editGoal(id) {
    // Get the goal from storage
    const goals = JSON.parse(localStorage.getItem('goals')) || [];
    const goal = goals.find(g => g.id === id);
    
    if (!goal) {
        showToast('Goal not found.', 'error');
        return;
    }
    
    // Populate the form
    document.getElementById('goal-name').value = goal.name;
    document.getElementById('goal-amount').value = goal.targetAmount;
    document.getElementById('goal-date').value = goal.targetDate || '';
    document.getElementById('goal-initial').value = goal.savedAmount;
    document.getElementById('goal-notes').value = goal.notes || '';
    
    // Show the modal
    document.getElementById('add-goal-modal').classList.remove('hidden');
    
    // Change the save button to update
    const saveButton = document.getElementById('btn-save-goal');
    saveButton.textContent = 'Update Goal';
    
    // Store the goal ID for updating
    saveButton.dataset.goalId = id;
    
    // Override the save function for updating
    saveButton.onclick = function() {
        updateGoal(id);
    };
}

// Update an existing goal
function updateGoal(id) {
    // Get form values
    const name = document.getElementById('goal-name').value;
    const amount = parseFloat(document.getElementById('goal-amount').value);
    const date = document.getElementById('goal-date').value;
    const initial = parseFloat(document.getElementById('goal-initial').value) || 0;
    const notes = document.getElementById('goal-notes').value;
    
    // Validate the form
    if (!name || isNaN(amount) || amount <= 0) {
        showToast('Please fill in all required fields with valid values.', 'error');
        return;
    }
    
    // Validate initial contribution
    if (initial > amount) {
        showToast('Saved amount cannot be greater than the target amount.', 'error');
        return;
    }
    
    // Get all goals
    let goals = JSON.parse(localStorage.getItem('goals')) || [];
    
    // Find and update the goal
    const index = goals.findIndex(g => g.id === id);
    
    if (index !== -1) {
        goals[index] = {
            ...goals[index],
            name: name,
            targetAmount: amount,
            targetDate: date || null,
            savedAmount: initial,
            notes: notes
        };
        
        // Save back to storage
        localStorage.setItem('goals', JSON.stringify(goals));
        
        // Show success message
        showToast('Savings goal updated successfully!', 'success');
        
        // Reset the form and close the modal
        resetGoalForm();
        document.getElementById('add-goal-modal').classList.add('hidden');
        
        // Reset the save button
        const saveButton = document.getElementById('btn-save-goal');
        saveButton.textContent = 'Create Goal';
        saveButton.dataset.goalId = '';
        saveButton.onclick = saveGoal;
        
        // Refresh the data
        loadGoals();
    } else {
        showToast('Goal not found.', 'error');
    }
}

// Delete a goal
function deleteGoal(id) {
    if (confirm('Are you sure you want to delete this savings goal?')) {
        // Get all goals
        let goals = JSON.parse(localStorage.getItem('goals')) || [];
        
        // Filter out the goal to delete
        goals = goals.filter(g => g.id !== id);
        
        // Save back to storage
        localStorage.setItem('goals', JSON.stringify(goals));
        
        // Show success message
        showToast('Savings goal deleted successfully!', 'success');
        
        // Refresh the data
        loadGoals();
    }
}

// Add funds to a goal
function addFundsToGoal(id) {
    // Get the goal from storage
    const goals = JSON.parse(localStorage.getItem('goals')) || [];
    const goal = goals.find(g => g.id === id);
    
    if (!goal) {
        showToast('Goal not found.', 'error');
        return;
    }
    
    // Prompt for amount
    const amount = parseFloat(prompt(`How much would you like to add to your "${goal.name}" goal? (¥)`));
    
    // Validate amount
    if (isNaN(amount) || amount <= 0) {
        showToast('Please enter a valid amount.', 'error');
        return;
    }
    
    // Check if adding this amount would exceed the goal
    const newTotal = goal.savedAmount + amount;
    if (newTotal > goal.targetAmount) {
        if (!confirm(`Adding ¥${amount} would exceed your goal by ¥${(newTotal - goal.targetAmount).toFixed(2)}. Continue anyway?`)) {
            return;
        }
    }
    
    // Update the goal
    const index = goals.findIndex(g => g.id === id);
    goals[index].savedAmount = newTotal;
    
    // Save back to storage
    localStorage.setItem('goals', JSON.stringify(goals));
    
    // Show success message
    showToast(`Added ¥${amount.toFixed(2)} to your "${goal.name}" goal!`, 'success');
    
    // Refresh the data
    loadGoals();
    
    // Also create a transaction for this contribution
    const transaction = {
        id: generateId(),
        date: new Date().toISOString().split('T')[0],
        description: `Contribution to ${goal.name}`,
        amount: -amount,
        category: 'savings',
        notes: `Savings goal contribution`
    };
    
    // Save transaction
    let transactions = JSON.parse(localStorage.getItem('transactions')) || [];
    transactions.push(transaction);
    localStorage.setItem('transactions', JSON.stringify(transactions));
    
    // Check if goal is completed
    if (newTotal >= goal.targetAmount) {
        showToast(`Congratulations! You've reached your "${goal.name}" savings goal!`, 'success');
    }
}
