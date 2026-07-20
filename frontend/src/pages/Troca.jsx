import { useState, useEffect } from 'react'
import IVDisplay from '../components/IVDisplay'
import { getSpriteUrl, getInventarioRival, enviarSolicitacaoTroca } from '../services/api'

export default function Troca({ meuPokemon, usuarioInicial, usuarioAtivo, onVoltar }) {
  const [rivalSelecionado, setRivalSelecionado] = useState(usuarioInicial || null)
  const [inventarioRival,  setInventarioRival]  = useState([])
  const [pokemonRival,     setPokemonRival]     = useState(null)
  const [carregando,       setCarregando]       = useState(false)
  const [enviando,         setEnviando]         = useState(false)
  const [sucesso,          setSucesso]          = useState(false)
  const [erro,             setErro]             = useState(null)

  // reage quando usuario clica num treinador na sidebar estando na tela de troca
  useEffect(() => {
    if (usuarioAtivo && usuarioAtivo.chave !== rivalSelecionado?.chave) {
      setRivalSelecionado(usuarioAtivo)
    }
  }, [usuarioAtivo])

  // carrega inventario do rival
  useEffect(() => {
    if (!rivalSelecionado?.chave) return
    setCarregando(true)
    setPokemonRival(null)
    setInventarioRival([])
    setErro(null)

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
      await enviarSolicitacaoTroca(
        rivalSelecionado.chave,
        meuPokemon.nomeCompleto || meuPokemon.nome,
        pokemonRival.nomeCompleto || pokemonRival.nome
      )
      setSucesso(true)
    } catch (e) {
      setErro(e.message)
    } finally {
      setEnviando(false)
    }
  }

  if (sucesso) {
    return (
      <div style={{
        flex: 1, background: '#3050C8',
        display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
        gap: '24px', height: '100vh',
      }}>
        <p style={{ fontFamily: 'var(--font)', fontSize: '18px', color: '#F8C800', textAlign: 'center', lineHeight: 2 }}>
          Solicitação enviada!<br />Aguardando o rival aceitar ✓
        </p>
        <button onClick={onVoltar} style={{
          background: '#D8D8D8', border: '3px solid #888', borderBottom: '5px solid #555',
          fontFamily: 'var(--font)', fontSize: '14px', color: '#181818',
          padding: '16px 24px', cursor: 'pointer',
        }}>Voltar ao Inventário</button>
      </div>
    )
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
        gap: '40px', padding: '16px', overflowY: 'auto',
      }}>
        {/* Meu Pokemon */}
        <PokemonTradeCard titulo="Você oferece" pokemon={meuPokemon} showIVs />

        {/* Pokebola */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px' }}>
          <img
            src="https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-ball.png"
            alt="Pokébola"
            style={{
              width: '80px', height: '80px', imageRendering: 'pixelated',
              filter: pokemonRival ? 'brightness(1.3)' : 'brightness(0.6)',
              transition: 'filter 0.3s',
            }}
          />
          <span style={{ fontFamily: 'var(--font)', fontSize: '16px', color: 'rgba(255,255,255,0.3)' }}>⇄</span>
        </div>

        {/* Lado do outro treinador */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', alignItems: 'center', maxWidth: '360px' }}>
          {rivalSelecionado ? (
            <>
              <p style={{ fontFamily: 'var(--font)', fontSize: '14px', color: '#F8C800' }}>
                {rivalSelecionado.nome}
              </p>

              {carregando && (
                <p style={{ fontFamily: 'var(--font)', fontSize: '12px', color: 'rgba(255,255,255,0.5)' }}>
                  Carregando inventário...
                </p>
              )}

              {!carregando && inventarioRival.length === 0 && !erro && (
                <p style={{ fontFamily: 'var(--font)', fontSize: '12px', color: 'rgba(255,255,255,0.4)', textAlign: 'center' }}>
                  Sem Pokémon disponíveis
                </p>
              )}

              {!carregando && inventarioRival.length > 0 && (
                <div style={{
                  display: 'grid', gridTemplateColumns: 'repeat(3, 80px)',
                  gap: '12px', background: 'rgba(0,0,0,0.3)',
                  border: '3px solid rgba(255,255,255,0.15)', padding: '12px',
                }}>
                  {inventarioRival.map((p, i) => (
                    <div key={`${p.id}-${i}`} onClick={() => setPokemonRival(p)} title={p.nome}
                      style={{
                        width: '80px', height: '80px',
                        background: pokemonRival?.nomeCompleto === p.nomeCompleto ? '#A8D8A8' : '#C8C8C8',
                        border: pokemonRival?.nomeCompleto === p.nomeCompleto ? '4px solid #4CAF50' : '4px solid #888',
                        cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
                        transform: pokemonRival?.nomeCompleto === p.nomeCompleto ? 'scale(1.1)' : 'scale(1)',
                        transition: 'transform 0.1s',
                      }}>
                      <img src={getSpriteUrl(p.id)} alt={p.nome}
                        style={{ width: '64px', height: '64px', imageRendering: 'pixelated' }} />
                    </div>
                  ))}
                </div>
              )}

              {/* IVs do Pokemon do rival selecionado */}
              {pokemonRival && (
                <PokemonTradeCard titulo="Você recebe" pokemon={pokemonRival} showIVs />
              )}
            </>
          ) : (
            <div style={{
              width: '240px', height: '280px',
              background: 'rgba(255,255,255,0.06)',
              border: '4px dashed rgba(255,255,255,0.2)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '24px',
            }}>
              <p style={{ fontFamily: 'var(--font)', fontSize: '12px', color: 'rgba(255,255,255,0.4)', textAlign: 'center', lineHeight: 2 }}>
                Selecione um treinador online na barra lateral
              </p>
            </div>
          )}
        </div>
      </div>

      {erro && (
        <div style={{ background: '#8B0000', padding: '12px 24px', fontFamily: 'var(--font)', fontSize: '12px', color: '#FFB3B3', textAlign: 'center' }}>
          ❌ {erro}
        </div>
      )}

      <div style={{
        background: 'rgba(0,0,0,0.35)', borderTop: '3px solid rgba(255,255,255,0.1)',
        padding: '16px 32px', display: 'flex', gap: '20px',
        alignItems: 'center', justifyContent: 'center',
      }}>
        <PixelBtn onClick={onVoltar} color="#D8D8D8" textColor="#181818">◀ Voltar</PixelBtn>
        <PixelBtn
          onClick={confirmarTroca}
          disabled={!pokemonRival || enviando}
          color={pokemonRival && !enviando ? '#D8D8D8' : '#888888'}
          textColor="#181818"
        >
          {enviando ? '⏳ Enviando...' : 'Confirmar Troca'}
        </PixelBtn>
      </div>
    </div>
  )
}

function PokemonTradeCard({ titulo, pokemon, showIVs }) {
  if (!pokemon) return null
  return (
    <div style={{
      background: '#D8D8D8', border: '4px solid #888888', borderBottom: '6px solid #555555',
      padding: '16px', display: 'flex', flexDirection: 'column',
      alignItems: 'center', gap: '8px', minWidth: '220px', maxWidth: '260px',
    }}>
      <span style={{ fontFamily: 'var(--font)', fontSize: '12px', color: '#666', textTransform: 'uppercase' }}>{titulo}</span>
      <span style={{ fontFamily: 'var(--font)', fontSize: '18px', color: '#181818', fontWeight: 'bold' }}>{pokemon.nome}</span>
      <img src={getSpriteUrl(pokemon.id)} alt={pokemon.nome}
        style={{ width: '96px', height: '96px', imageRendering: 'pixelated' }} />
      {showIVs && pokemon.hp !== undefined && (
        <div style={{ width: '100%', borderTop: '2px solid #A8A8A8', paddingTop: '10px' }}>
          <IVDisplay pokemon={pokemon} compact />
        </div>
      )}
    </div>
  )
}

function PixelBtn({ children, onClick, disabled, color, textColor }) {
  return (
    <button onClick={onClick} disabled={disabled} style={{
      background: color, border: '4px solid #555', borderBottom: '6px solid #333',
      fontFamily: 'var(--font)', fontSize: '14px', color: textColor,
      padding: '14px 24px', cursor: disabled ? 'not-allowed' : 'pointer',
      opacity: disabled ? 0.7 : 1,
    }}>{children}</button>
  )
}