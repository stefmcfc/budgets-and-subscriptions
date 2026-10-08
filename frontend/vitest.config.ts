import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test-setup.ts',
      coverage: {
        provider: 'v8',
        reporter: ['text', 'lcov', 'json-summary'],
        exclude: ['**/*.test.ts', '**/*.test.tsx', 'src/main.tsx', '**/*.d.ts'],
      },
    },
  }),
)
