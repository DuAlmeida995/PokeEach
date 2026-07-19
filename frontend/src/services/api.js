const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:9081'

async function get(path) {
  const res = await fetch(`${BASE_URL}${path}`)
  if (!res.ok) throw new Error(`GET ${path} → ${res.status}`)
  return res.json()
}

async function post(path, body) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.erro || `POST ${path} → ${res.status}`)
  }
  return res.json()
}

export async function getStatus()              { return get('/status') }
export async function getMeuInventario()       { return get('/inventario') }
export async function getPeers()               { return get('/peers') }
export async function minerar()                { return post('/minerar', {}) }

/**
 * Busca inventário de um peer pelo seu endereço IP:porta P2P.
 * O backend faz o proxy para o /inventario do nó do rival.
 */
export async function getInventarioRival(enderecoP2P) {
  const encoded = encodeURIComponent(enderecoP2P)
  return get(`/inventario?peer=${encoded}`)
}

export async function enviarTransacao(destinatarioChave, idPokemon) {
  return post('/transacao', { destinatario: destinatarioChave, idPokemon })
}

export function getSpriteUrl(pokemonId) {
  if (!pokemonId || pokemonId <= 0) return ''
  return `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${pokemonId}.png`
}

export async function fetchPokemonStats(pokemonId) {
  try {
    const res  = await fetch(`https://pokeapi.co/api/v2/pokemon/${pokemonId}`)
    const data = await res.json()
    return {
      hp:     data.stats.find(s => s.stat.name === 'hp')?.base_stat      ?? '—',
      ataque: data.stats.find(s => s.stat.name === 'attack')?.base_stat  ?? '—',
      defesa: data.stats.find(s => s.stat.name === 'defense')?.base_stat ?? '—',
      veloc:  data.stats.find(s => s.stat.name === 'speed')?.base_stat   ?? '—',
      tipo:   data.types.map(t => t.type.name).join(' / '),
    }
  } catch {
    return { hp: '—', ataque: '—', defesa: '—', veloc: '—', tipo: '—' }
  }
}

/** Verifica se há uma solicitação de troca pendente para este nó */
export async function getNotificacaoTroca() { return get('/troca/pendente') }

/** Responde a uma solicitação de troca pendente */
export async function responderTroca(aceitar) {
  return post('/troca/responder', { aceitar })
}

/** Envia solicitação de troca bilateral para o rival */
export async function enviarSolicitacaoTroca(enderecoRival, meuPokemon, pokemonSolicitado) {
  return post('/troca/solicitar', { enderecoRival, meuPokemon, pokemonSolicitado })
}