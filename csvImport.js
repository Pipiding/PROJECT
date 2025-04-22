document.addEventListener('DOMContentLoaded', function() {
    // Set up CSV import modal events
    setupCSVImportModal();
});

// Set up CSV import modal events
function setupCSVImportModal() {
    // Get modal elements
    const modal = document.getElementById('import-csv-modal');
    const importButton = document.getElementById('btn-import-csv');
    const cancelButton = document.getElementById('btn-cancel-import');
    const fileUpload = document.getElementById('file-upload');
    
    // Handle file selection
    fileUpload.addEventListener('change', function(e) {
        const file = e.target.files[0];
        if (file) {
            parseCSVFile(file);
        }
    });
    
    // Import transactions
    importButton.addEventListener('click', function() {
        importCSVTransactions();
    });
    
    // Cancel and close modal
    cancelButton.addEventListener('click', function() {
        closeCSVModal();
    });
    
    // Close modal when clicking outside
    modal.addEventListener('click', function(e) {
        if (e.target === modal) {
            closeCSVModal();
        }
    });
    
    // Drag and drop handling
    const dropArea = document.querySelector('.border-dashed');
    
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        dropArea.addEventListener(eventName, preventDefaults, false);
    });
    
    function preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }
    
    ['dragenter', 'dragover'].forEach(eventName => {
        dropArea.addEventListener(eventName, highlight, false);
    });
    
    ['dragleave', 'drop'].forEach(eventName => {
        dropArea.addEventListener(eventName, unhighlight, false);
    });
    
    function highlight() {
        dropArea.classList.add('border-blue-300', 'bg-blue-50');
    }
    
    function unhighlight() {
        dropArea.classList.remove('border-blue-300', 'bg-blue-50');
    }
    
    dropArea.addEventListener('drop', handleDrop, false);
    
    function handleDrop(e) {
        const dt = e.dataTransfer;
        const file = dt.files[0];
        
        if (file) {
            fileUpload.files = dt.files;
            parseCSVFile(file);
        }
    }
}

// Close the CSV import modal
function closeCSVModal() {
    document.getElementById('import-csv-modal').classList.add('hidden');
    document.getElementById('file-upload').value = '';
    document.getElementById('csv-preview').classList.add('hidden');
    document.getElementById('csv-mapping').classList.add('hidden');
    document.getElementById('csv-preview-content').innerHTML = '';
}

// Parse the uploaded CSV file
function parseCSVFile(file) {
    // Check if it's a CSV file
    if (file.type !== 'text/csv' && !file.name.endsWith('.csv')) {
        showToast('Please upload a valid CSV file.', 'error');
        return;
    }
    
    // Check file size
    if (file.size > 10 * 1024 * 1024) { // 10MB limit
        showToast('File size exceeds 10MB limit.', 'error');
        return;
    }
    
    const reader = new FileReader();
    
    reader.onload = function(e) {
        const contents = e.target.result;
        try {
            // Parse CSV
            const data = parseCSV(contents);
            
            if (data.length === 0) {
                showToast('The CSV file appears to be empty.', 'error');
                return;
            }
            
            // Show preview
            showCSVPreview(data);
            
            // Show mapping options
            showCSVMapping(data[0].length);
        } catch (error) {
            showToast('Error parsing CSV file: ' + error.message, 'error');
        }
    };
    
    reader.onerror = function() {
        showToast('Error reading file.', 'error');
    };
    
    reader.readAsText(file);
}

// Simple CSV parser
function parseCSV(text) {
    const lines = text.split(/\r\n|\n/);
    const result = [];
    
    for (let i = 0; i < lines.length; i++) {
        if (lines[i].trim() === '') continue;
        
        // Handle quoted fields
        const row = [];
        let inQuote = false;
        let field = '';
        
        for (let j = 0; j < lines[i].length; j++) {
            const char = lines[i][j];
            
            if (char === '"') {
                inQuote = !inQuote;
            } else if (char === ',' && !inQuote) {
                row.push(field);
                field = '';
            } else {
                field += char;
            }
        }
        
        row.push(field); // Add the last field
        result.push(row);
    }
    
    return result;
}

// Show CSV preview
function showCSVPreview(data) {
    const previewElement = document.getElementById('csv-preview');
    const previewContent = document.getElementById('csv-preview-content');
    
    // Clear previous content
    previewContent.innerHTML = '';
    
    // Show preview section
    previewElement.classList.remove('hidden');
    
    // Add rows to preview (up to 5)
    const rowsToShow = Math.min(5, data.length);
    
    for (let i = 0; i < rowsToShow; i++) {
        const row = document.createElement('tr');
        
        // Add cells to row
        for (let j = 0; j < data[i].length; j++) {
            const cell = document.createElement('td');
            cell.className = 'px-3 py-2 whitespace-nowrap text-sm text-gray-500';
            cell.textContent = data[i][j];
            row.appendChild(cell);
        }
        
        previewContent.appendChild(row);
    }
}

// Show CSV column mapping options
function showCSVMapping(columnCount) {
    const mappingElement = document.getElementById('csv-mapping');
    
    // Show mapping section
    mappingElement.classList.remove('hidden');
    
    // Update mapping dropdowns with the correct number of columns
    const dropdowns = [
        document.getElementById('date-column'),
        document.getElementById('description-column'),
        document.getElementById('amount-column'),
        document.getElementById('category-column')
    ];
    
    dropdowns.forEach(dropdown => {
        // Clear previous options
        dropdown.innerHTML = '';
        
        // Add options based on column count
        for (let i = 0; i < columnCount; i++) {
            const option = document.createElement('option');
            option.value = i;
            option.textContent = `Column ${i + 1}`;
            dropdown.appendChild(option);
        }
    });
    
    // Set default mappings
    if (columnCount >= 4) {
        document.getElementById('date-column').value = 0;
        document.getElementById('description-column').value = 1;
        document.getElementById('amount-column').value = 2;
        document.getElementById('category-column').value = 3;
    }
}

// Import transactions from CSV
function importCSVTransactions() {
    const fileInput = document.getElementById('file-upload');
    
    if (!fileInput.files || fileInput.files.length === 0) {
        showToast('Please select a CSV file to import.', 'error');
        return;
    }
    
    const file = fileInput.files[0];
    
    // Get column mappings
    const dateColumn = parseInt(document.getElementById('date-column').value);
    const descriptionColumn = parseInt(document.getElementById('description-column').value);
    const amountColumn = parseInt(document.getElementById('amount-column').value);
    const categoryColumn = parseInt(document.getElementById('category-column').value);
    
    const reader = new FileReader();
    
    reader.onload = function(e) {
        const contents = e.target.result;
        try {
            // Parse CSV
            const data = parseCSV(contents);
            
            if (data.length === 0) {
                showToast('No data to import.', 'error');
                return;
            }
            
            // Process data into transactions
            const transactions = [];
            let successCount = 0;
            let errorCount = 0;
            
            // Skip header row
            for (let i = 1; i < data.length; i++) {
                const row = data[i];
                
                // Skip if row doesn't have enough columns
                if (row.length <= Math.max(dateColumn, descriptionColumn, amountColumn, categoryColumn)) {
                    errorCount++;
                    continue;
                }
                
                // Get values
                const dateStr = row[dateColumn].trim();
                const description = row[descriptionColumn].trim();
                const amountStr = row[amountColumn].trim().replace(/[^\d.-]/g, ''); // Remove currency symbols
                const category = row[categoryColumn] ? row[categoryColumn].trim().toLowerCase() : 'other';
                
                // Parse date
                let date = parseDate(dateStr);
                if (!date) {
                    errorCount++;
                    continue;
                }
                
                // Parse amount
                const amount = parseFloat(amountStr);
                if (isNaN(amount)) {
                    errorCount++;
                    continue;
                }
                
                // Create transaction
                const transaction = {
                    id: generateId(),
                    date: date,
                    description: description,
                    amount: amount,
                    category: validateCategory(category),
                    notes: 'Imported from CSV'
                };
                
                transactions.push(transaction);
                successCount++;
            }
            
            // Save transactions to storage
            if (transactions.length > 0) {
                let existingTransactions = JSON.parse(localStorage.getItem('transactions')) || [];
                existingTransactions = existingTransactions.concat(transactions);
                localStorage.setItem('transactions', JSON.stringify(existingTransactions));
                
                // Show success message
                showToast(`Successfully imported ${successCount} transactions. ${errorCount > 0 ? errorCount + ' rows were skipped due to errors.' : ''}`, 'success');
                
                // Refresh data
                loadTransactions();
                
                // If on dashboard, also refresh that data
                if (!document.getElementById('dashboard-section').classList.contains('hidden')) {
                    loadDashboardData();
                }
                
                // Close the modal
                closeCSVModal();
            } else {
                showToast('No valid transactions found in the CSV file.', 'error');
            }
        } catch (error) {
            showToast('Error processing CSV file: ' + error.message, 'error');
        }
    };
    
    reader.onerror = function() {
        showToast('Error reading file.', 'error');
    };
    
    reader.readAsText(file);
}

// Parse date from string
function parseDate(dateStr) {
    // Try different date formats
    let date;
    
    // ISO format: YYYY-MM-DD
    if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
        date = dateStr;
    }
    // MM/DD/YYYY
    else if (/^\d{1,2}\/\d{1,2}\/\d{4}$/.test(dateStr)) {
        const parts = dateStr.split('/');
        date = `${parts[2]}-${parts[0].padStart(2, '0')}-${parts[1].padStart(2, '0')}`;
    }
    // DD/MM/YYYY
    else if (/^\d{1,2}\/\d{1,2}\/\d{4}$/.test(dateStr)) {
        const parts = dateStr.split('/');
        date = `${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}`;
    }
    // Other formats...
    else {
        // Try to parse with Date object
        const d = new Date(dateStr);
        if (!isNaN(d.getTime())) {
            date = d.toISOString().split('T')[0];
        } else {
            return null;
        }
    }
    
    return date;
}

// Validate and normalize category
function validateCategory(category) {
    const validCategories = ['food', 'transport', 'utilities', 'entertainment', 'income', 'other'];
    
    // Normalize category
    category = category.toLowerCase().trim();
    
    // Map common variations
    if (['groceries', 'restaurant', 'dining', 'meal'].includes(category)) {
        return 'food';
    } else if (['travel', 'gas', 'fuel', 'subway', 'bus', 'taxi', 'uber'].includes(category)) {
        return 'transport';
    } else if (['electric', 'water', 'gas', 'bill', 'rent', 'mortgage'].includes(category)) {
        return 'utilities';
    } else if (['movie', 'music', 'game', 'subscription', 'hobby'].includes(category)) {
        return 'entertainment';
    } else if (['salary', 'bonus', 'refund', 'gift', 'interest'].includes(category)) {
        return 'income';
    }
    
    // Return category if valid, otherwise 'other'
    return validCategories.includes(category) ? category : 'other';
}
