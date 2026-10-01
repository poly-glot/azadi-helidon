/**
 * Stripe Payment Module
 * Initializes Stripe Elements for card payments on the make-a-payment page.
 */

export function initStripePayment() {
  const cardContainer = document.getElementById('card-element');
  const paymentForm = document.getElementById('payment-form');

  if (!cardContainer || !paymentForm) return;

  const publishableKey = paymentForm.dataset.stripeKey;
  if (!publishableKey) {
    console.warn('Stripe publishable key not found on #payment-form[data-stripe-key]');
    return;
  }

  if (typeof Stripe === 'undefined') {
    console.warn('Stripe.js not loaded. Ensure the script is included in the page.');
    return;
  }

  const stripe = Stripe(publishableKey);
  const elements = stripe.elements();
  const errorDisplay = document.getElementById('card-errors');
  const submitButton = paymentForm.querySelector('[type="submit"]');

  const cardElement = elements.create('card', {
    style: {
      base: {
        color: '#282828',
        fontFamily: "'Poppins', Arial, sans-serif",
        fontSize: '14px',
        '::placeholder': { color: '#afb4b7' },
      },
      invalid: {
        color: '#e4042b',
        iconColor: '#e4042b',
      },
    },
  });
  cardElement.mount('#card-element');

  cardElement.on('change', (event) => {
    showError(event.error ? event.error.message : '');
  });

  function showError(message) {
    if (errorDisplay) errorDisplay.textContent = message;
  }

  function setSubmitting(busy) {
    if (!submitButton) return;
    submitButton.disabled = busy;
    submitButton.textContent = busy ? 'Processing...' : 'Pay';
  }

  function handleFailure(message) {
    showError(message);
    setSubmitting(false);
  }

  paymentForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    setSubmitting(true);

    try {
      const amountInput = paymentForm.querySelector('[name="amount"]');
      const agreementSelect = paymentForm.querySelector('[name="agreementId"]');
      const csrfInput = paymentForm.querySelector('[name="_csrf"]');
      const csrfToken = csrfInput?.value ?? '';

      const amountPence = Math.round(parseFloat(amountInput.value) * 100);
      if (isNaN(amountPence) || amountPence < 100) {
        return handleFailure('Please enter a valid amount (minimum \u00a31.00).');
      }

      const response = await fetch(paymentForm.action, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': csrfToken,
        },
        body: JSON.stringify({
          amountPence,
          agreementId: agreementSelect ? parseInt(agreementSelect.value, 10) : null,
        }),
      });

      const result = await response.json();

      if (result.error) {
        return handleFailure(result.error);
      }

      const { error } = await stripe.confirmCardPayment(result.clientSecret, {
        payment_method: { card: cardElement },
      });

      if (error) {
        return handleFailure(error.message);
      }

      window.location.href = '/finance/make-a-payment?success=true';
    } catch {
      handleFailure('An unexpected error occurred. Please try again.');
    }
  });
}
