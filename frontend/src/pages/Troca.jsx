import { useState, useEffect } from 'react'
import { getSpriteUrl, getInventarioRival, enviarTransacao } from '../services/api'

export default function Troca({ meuPokemon, usuarioInicial, usuarioAtivo, onVoltar }) {
  const [rivalSelecionado, setRivalSelecionado] = useState(usuarioInicial || null)
  const [inventarioRival,  setInventarioRival]  = useState([])
  const [pokemonRival,     setPokemonRival]      = useState(null)
  const [carregando,       setCarregando]        = useState(false)
  const [enviando,         setEnviando]          = useState(false)
  const [sucesso,          setSucesso]           = useState(false)
  const [erro,             setErro]              = useState(null)

  // Reage quando usuário clica num treinador na sidebar estando já na tela de troca
  useEffect(() => {
    if (usuarioAtivo && usuarioAtivo.chave !== rivalSelecionado?.chave) {
      setRivalSelecionado(usuarioAtivo)
    }
  }, [usuarioAtivo])

  // Carrega inventário do rival pelo endereço P2P (IP:porta)
  useEffect(() => {
    if (!rivalSelecionado?.chave) return
    setCarregando(true)
    setPokemonRival(null)
    setInventarioRival([])
    setErro(null)

    // rivalSelecionado.chave = "127.0.0.1:8082" (endereço P2P)
    getInventarioRival(rivalSelecionado.chave)
      .then(data => setInventarioRival(data.pokemon || []))
      .catch(e => setErro('Não foi possível carregar o inventário: ' + e.message))
      .finally(() => setCarregando(false))
  }, [rivalSelecionado])

  async function confirmarTroca() {
    if (!meuPokemon || !pokemonRival || !rivalSelecionado) return
    setEnviando(true)
    setErro(null)
    try {
      // Usa a chavePublica RSA do rival para a TX
      const destChave = rivalSelecionado.chavePublica
      if (!destChave) throw new Error('Chave pública do rival não disponível.')
      await enviarTransacao(destChave, meuPokemon.nome)
      setSucesso(true)
    } catch (e) {
      setErro(e.message)
    } finally {
      setEnviando(false)
    }
  }

  if (sucesso) {
    return <TelaSucesso meuPokemon={meuPokemon} pokemonRival={pokemonRival} onVoltar={onVoltar} />
  }

  return (
    <div style={{
      flex: 1, background: '#3050C8',
      display: 'flex', flexDirection: 'column',
      height: '100vh', overflow: 'hidden',
    }}>
      <div style={{
        flex: 1, display: 'flex',
        alignItems: 'center', justifyContent: 'center',
        gap: '24px', padding: '20px', overflowY: 'auto',
      }}>
        {/* Meu Pokémon */}
        <PokemonTradeCard titulo="Você oferece" pokemon={meuPokemon} />

        {/* Pokébola */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
          <img
            src="https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-ball.png"
            alt="Pokébola"
            style={{
              width: '64px', height: '64px', imageRendering: 'pixelated',
              filter: pokemonRival ? 'brightness(1.3)' : 'brightness(0.6)',
              transition: 'filter 0.3s',
            }}
          />
          <span style={{ fontFamily: 'var(--font)', fontSize: '10px', color: 'rgba(255,255,255,0.3)' }}>⇄</span>
        </div>

        {/* Lado do rival */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', alignItems: 'center' }}>
          {rivalSelecionado ? (
            <>
              <p style={{ fontFamily: 'var(--font)', fontSize: '7px', color: '#F8C800' }}>
                {rivalSelecionado.nome}
              </p>

              {carregando && (
                <p style={{ fontFamily: 'var(--font)', fontSize: '7px', color: 'rgba(255,255,255,0.5)' }}>
                  Carregando inventário...
                </p>
              )}

              {!carregando && inventarioRival.length === 0 && !erro && (
                <p style={{ fontFamily: 'var(--font)', fontSize: '7px', color: 'rgba(255,255,255,0.4)', textAlign: 'center' }}>
                  Sem Pokémon disponíveis
                </p>
              )}

              {!carregando && inventarioRival.length > 0 && (
                <div style={{
                  display: 'grid', gridTemplateColumns: 'repeat(3, 60px)',
                  gap: '8px', background: 'rgba(0,0,0,0.3)',
                  border: '3px solid rgba(255,255,255,0.15)', padding: '10px',
                }}>
                  {inventarioRival.map((p, i) => (
                    <div key={`${p.id}-${i}`} onClick={() => setPokemonRival(p)} title={p.nome}
                      style={{
                        width: '60px', height: '60px',
                        background: pokemonRival?.nome === p.nome ? '#A8D8A8' : '#C8C8C8',
                        border: pokemonRival?.nome === p.nome ? '3px solid #4CAF50' : '3px solid #888',
                        cursor: 'pointer', display: 'flex',
                        alignItems: 'center', justifyContent: 'center',
                        transform: pokemonRival?.nome === p.nome ? 'scale(1.1)' : 'scale(1)',
                        transition: 'transform 0.1s',
                      }}>
                      <img src={getSpriteUrl(p.id)} alt={p.nome}
                        style={{ width: '44px', height: '44px', imageRendering: 'pixelated' }} />
                    </div>
                  ))}
                </div>
              )}

              {pokemonRival && (
                <PokemonTradeCard titulo="Você recebe" pokemon={pokemonRival} />
              )}
            </>
          ) : (
            <div style={{
              width: '180px', height: '200px',
              background: 'rgba(255,255,255,0.06)',
              border: '3px dashed rgba(255,255,255,0.2)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px',
            }}>
              <p style={{ fontFamily: 'var(--font)', fontSize: '7px', color: 'rgba(255,255,255,0.4)', textAlign: 'center', lineHeight: 2 }}>
                Selecione um treinador online na barra lateral
              </p>
            </div>
          )}
        </div>
      </div>

      {erro && (
        <div style={{ background: '#8B0000', padding: '8px 16px', fontFamily: 'var(--font)', fontSize: '7px', color: '#FFB3B3', textAlign: 'center' }}>
          ❌ {erro}
        </div>
      )}

      <div style={{
        background: 'rgba(0,0,0,0.35)', borderTop: '3px solid rgba(255,255,255,0.1)',
        padding: '12px 24px', display: 'flex', gap: '16px', alignItems: 'center', justifyContent: 'center',
      }}>
        <PixelBtn onClick={onVoltar} color="#D8D8D8" textColor="#181818">◀ Voltar</PixelBtn>
        <PixelBtn
          onClick={confirmarTroca}
          disabled={!pokemonRival || enviando || !rivalSelecionado?.chavePublica}
          color={pokemonRival && !enviando ? '#D8D8D8' : '#888888'}
          textColor="#181818"
        >
          {enviando ? '⏳ Enviando TX...' : 'Confirmar Troca'}
        </PixelBtn>
      </div>
    </div>
  )
}

function PokemonTradeCard({ titulo, pokemon }) {
  return (
    <div style={{
      background: '#D8D8D8', border: '4px solid #888888', borderBottom: '6px solid #555555',
      padding: '12px', display: 'flex', flexDirection: 'column',
      alignItems: 'center', gap: '8px', minWidth: '150px',
    }}>
      <span style={{ fontFamily: 'var(--font)', fontSize: '6px', color: '#666', textTransform: 'uppercase' }}>{titulo}</span>
      <span style={{ fontFamily: 'var(--font)', fontSize: '9px', color: '#181818' }}>{pokemon?.nome}</span>
      {pokemon?.id > 0 && (
        <img src={getSpriteUrl(pokemon.id)} alt={pokemon.nome}
          style={{ width: '80px', height: '80px', imageRendering: 'pixelated' }} />
      )}
    </div>
  )
}

function PixelBtn({ children, onClick, disabled, color, textColor }) {
  return (
    <button onClick={onClick} disabled={disabled} style={{
      background: color, border: '3px solid #555', borderBottom: '5px solid #333',
      fontFamily: 'var(--font)', fontSize: '7px', color: textColor,
      padding: '10px 16px', cursor: disabled ? 'not-allowed' : 'pointer',
      opacity: disabled ? 0.7 : 1,
    }}>
      {children}
    </button>
  )
}

function TelaSucesso({ meuPokemon, pokemonRival, onVoltar }) {
  return (
    <div style={{
      flex: 1, background: '#3050C8',
      display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center',
      gap: '24px', height: '100vh',
    }}>
      <p style={{ fontFamily: 'var(--font)', fontSize: '10px', color: '#F8C800', textAlign: 'center', lineHeight: 2 }}>
        Troca realizada!<br />TX enviada à rede ✓
      </p>
      {pokemonRival && <PokemonTradeCard titulo="você recebeu" pokemon={pokemonRival} />}
      <button onClick={onVoltar} style={{
        background: '#D8D8D8', border: '3px solid #888', borderBottom: '5px solid #555',
        fontFamily: 'var(--font)', fontSize: '8px', color: '#181818',
        padding: '12px 20px', cursor: 'pointer',
      }}>
        Voltar ao Inventário
      </button>
    </div>
  )
}