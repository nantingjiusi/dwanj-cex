import { createApp } from 'vue';
import Toast from '../components/Toast.vue';

export function useToast() {
  const showToast = (message, options = {}) => {
    const { type = 'info', duration = 3000 } = options;

    // 创建一个临时的div容器
    const container = document.createElement('div');
    document.body.appendChild(container);

    // 创建Toast组件实例并挂载
    const toastApp = createApp(Toast, {
      message,
      type,
      duration,
    });
    
    toastApp.mount(container);

    // 在动画结束后销毁组件和容器
    setTimeout(() => {
      toastApp.unmount();
      document.body.removeChild(container);
    }, duration + 500); // 加上动画时间
  };

  return { showToast };
}
