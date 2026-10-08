import { createApp } from 'vue';
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue';
import './console.css';

const app = createApp(App)
app.use(VueQueryPlugin)
app.mount('#app')
