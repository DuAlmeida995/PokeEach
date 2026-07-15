/**
 * Camada de comunicação com o backend Java.
 * A porta REST é 9000 + (portaP2P - 8000):
 *   nó 8081 → REST 9081
 *   nó 8082 → REST 9082
 *
 * Em desenvolvimento, aponta para o nó local na porta configurada.
 */

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

// ── Endpoints ──────────────────────────────────────────────────────

/** Retorna status do nó: height, peers, mempool, chavePublica, nomeTreinador */
export async function getStatus() {
  return get('/status')
}

/** Retorna inventário do treinador local */
export async function getMeuInventario() {
  return get('/inventario')
}

/** Retorna inventário de outro treinador pela chave pública Base64 */
export async function getInventarioRival(chaveBase64) {
  const chaveEncoded = encodeURIComponent(chaveBase64)
  return get(`/inventario/${chaveEncoded}`)
}

/** Retorna lista de peers ativos: ["127.0.0.1:8082", ...] */
export async function getPeers() {
  return get('/peers')
}

/**
 * Minera um bloco.
 * Retorna: { sucesso, height, hash, rewardPokemon, rewardId }
 */
export async function minerar() {
  return post('/minerar', {})
}

/**
 * Envia uma transação de troca.
 * @param {string} destinatario - chave pública Base64 do destinatário
 * @param {string} idPokemon    - nome do Pokémon a transferir
 * Retorna: { sucesso, transactionId, pokemon }
 */
export async function enviarTransacao(destinatario, idPokemon) {
  return post('/transacao', { destinatario, idPokemon })
}

// ── Utilitários ────────────────────────────────────────────────────

export function getSpriteUrl(pokemonId) {
  return `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${pokemonId}.png`
}

export async function fetchPokemonStats(pokemonId) {
  try {
    const res  = await fetch(`https://pokeapi.co/api/v2/pokemon/${pokemonId}`)
    const data = await res.json()
    return {
      hp:     data.stats.find(s => s.stat.name === 'hp')?.base_stat       ?? '—',
      ataque: data.stats.find(s => s.stat.name === 'attack')?.base_stat   ?? '—',
      defesa: data.stats.find(s => s.stat.name === 'defense')?.base_stat  ?? '—',
      veloc:  data.stats.find(s => s.stat.name === 'speed')?.base_stat    ?? '—',
      tipo:   data.types.map(t => t.type.name).join(' / '),
    }
  } catch {
    return { hp: '—', ataque: '—', defesa: '—', veloc: '—', tipo: '—' }
  }
}