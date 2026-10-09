import components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import * as elementPlusIcons from '@element-plus/icons-vue'

const elementPlusIconNames = new Set(Object.keys(elementPlusIcons))

export default function createComponents() {
  return components({
    dts: 'src/types/components.d.ts',
    resolvers: [
      ElementPlusResolver({ importStyle: 'css' }),
      (name: string) => elementPlusIconNames.has(name) ? { name, from: '@element-plus/icons-vue' } : undefined
    ]
  })
}
