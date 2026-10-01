/**
 * Inline Edit Module
 * Toggles between display and edit views on contact detail fields.
 */

export function initInlineEdit() {
  const editBlocks = document.querySelectorAll('[data-inline-edit]');

  editBlocks.forEach((block) => {
    const editButton = block.querySelector('[data-inline-edit-trigger]');
    const cancelButton = block.querySelector('[data-inline-edit-cancel]');

    if (editButton) {
      editButton.addEventListener('click', (event) => {
        event.preventDefault();
        block.classList.add('is-editing');

        // Focus the first input in the edit fields
        const firstInput = block.querySelector('.c-inline-edit__fields input');
        if (firstInput) {
          firstInput.focus();
        }
      });
    }

    if (cancelButton) {
      cancelButton.addEventListener('click', (event) => {
        event.preventDefault();
        block.classList.remove('is-editing');

        // Return focus to the edit button
        if (editButton) {
          editButton.focus();
        }
      });
    }
  });
}
