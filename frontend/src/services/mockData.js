export const MEU_TREINADOR = {
  nome: 'Ash',
  chavePublica: 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...',
}

export const USUARIOS_ONLINE = [
  { nome: 'PixelPanda_99',    chave: 'chave_panda' },
  { nome: 'ShadowToast',      chave: 'chave_shadow' },
  { nome: 'Capitao_Ferrugem', chave: 'chave_cap' },
  { nome: 'CyberGlitch',      chave: 'chave_cyber' },
  { nome: 'BatataCosmica',    chave: 'chave_batata' },
  { nome: 'Vortex_BR',        chave: 'chave_vortex' },
  { nome: 'Lofi_Ninja',       chave: 'chave_lofi' },
  { nome: 'Sr_Meow',          chave: 'chave_meow' },
]

export const MEU_INVENTARIO = [
  { id: 1,   nome: 'Bulbasaur'  },
  { id: 151, nome: 'Mew'        },
  { id: 4,   nome: 'Charmander' },
  { id: 28,  nome: 'Sandslash'  },
  { id: 65,  nome: 'Alakazam'   },
  { id: 39,  nome: 'Jigglypuff' },
  { id: 12,  nome: 'Butterfree' },
  { id: 7,   nome: 'Squirtle'   },
]

export const INVENTARIO_RIVAL = {
  chave_shadow: [
    { id: 6,   nome: 'Charizard' },
    { id: 25,  nome: 'Pikachu'   },
    { id: 143, nome: 'Snorlax'   },
    { id: 131, nome: 'Lapras'    },
  ],
  chave_panda: [
    { id: 94,  nome: 'Gengar'    },
    { id: 130, nome: 'Gyarados'  },
    { id: 149, nome: 'Dragonite' },
  ],
}

export function getSpriteUrl(pokemonId) {
  return `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${pokemonId}.png`
}

export async function fetchPokemonStats(pokemonId) {
  try {
    const res = await fetch(`https://pokeapi.co/api/v2/pokemon/${pokemonId}`)
    const data = await res.json()
    return {
      hp:     data.stats.find(s => s.stat.name === 'hp')?.base_stat ?? '—',
      ataque: data.stats.find(s => s.stat.name === 'attack')?.base_stat ?? '—',
      defesa: data.stats.find(s => s.stat.name === 'defense')?.base_stat ?? '—',
      veloc:  data.stats.find(s => s.stat.name === 'speed')?.base_stat ?? '—',
      tipo:   data.types.map(t => t.type.name).join(' / '),
    }
  } catch {
    return { hp: '—', ataque: '—', defesa: '—', veloc: '—', tipo: '—' }
  }
}