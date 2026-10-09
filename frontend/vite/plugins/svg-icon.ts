import fs from 'node:fs'
import path from 'path'
import type { PluginOption } from 'vite'

const virtualModule = 'virtual:svg-icons-register'
const resolvedVirtualModule = '\0' + virtualModule

function iconFiles(directory: string): string[] {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const absolute = path.join(directory, entry.name)
    return entry.isDirectory() ? iconFiles(absolute) : entry.name.endsWith('.svg') ? [absolute] : []
  })
}

function symbol(directory: string, file: string): string {
  const source = fs.readFileSync(file, 'utf8').replace(/<\?(?:xml)[^>]*>|<!DOCTYPE[^>]*>|<!--[\s\S]*?-->/gi, '').trim()
  const match = source.match(/<svg\b([^>]*)>([\s\S]*?)<\/svg>/i)
  if (!match) throw new Error(`Invalid SVG icon: ${file}`)
  const attributes = match[1]
  const viewBox = attributes.match(/\bviewBox=(['"])(.*?)\1/i)?.[2]
  const relative = path.relative(directory, file).replace(/\\/g, '/').replace(/\.svg$/i, '')
  const id = `icon-${relative.replace(/[^A-Za-z0-9_-]/g, '-')}`
  return `<symbol id="${id}"${viewBox ? ` viewBox="${viewBox}"` : ''}>${match[2]}</symbol>`
}

export default function createSvgIcon(_isBuild: boolean): PluginOption {
  const directory = path.resolve(process.cwd(), 'src/assets/icons/svg')
  return {
    name: 'local-svg-sprite',
    resolveId(id) { return id === virtualModule ? resolvedVirtualModule : undefined },
    load(id) {
      if (id !== resolvedVirtualModule) return undefined
      const sprite = `<svg aria-hidden="true" style="position:absolute;width:0;height:0;overflow:hidden" xmlns="http://www.w3.org/2000/svg">${iconFiles(directory).sort().map(file => symbol(directory, file)).join('')}</svg>`
      return `const sprite=${JSON.stringify(sprite)};document.body.insertAdjacentHTML('afterbegin',sprite);export default sprite;`
    }
  }
}
