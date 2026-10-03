import { ref } from 'vue';
import type { ToastState } from '../types';

const toast = ref<ToastState>({ message: '', error: false, visible: false });
let toastTimer: ReturnType<typeof setTimeout> | undefined;

export function useToast() {
  function notify(message: string, error = false): void {
    toast.value = { message, error, visible: true };
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toast.value.visible = false;
    }, 3600);
  }

  return { toast, notify };
}
