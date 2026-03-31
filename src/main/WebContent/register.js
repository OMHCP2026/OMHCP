// Age validation before form submission
document.getElementById("registerForm").addEventListener("submit", function(e){
    const age = document.getElementById("age").value;
    if(age < 18){
        alert("Age must be 18+");
        e.preventDefault();
    }
});