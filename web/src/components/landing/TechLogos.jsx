import { Bug } from 'lucide-react'
import {
  siApachekafka, siApachemaven, siCaddy, siDocker, siFlyway, siJunit5, siOpenjdk, siPostgresql,
  siReact, siRedis, siSpringboot, siSwagger, siTailwindcss, siVite,
} from 'simple-icons'
import { Marquee } from '../ui/Marquee'

// Logos come from Simple Icons (CC0). TigerBeetle has no entry there, so it gets a neutral stand-in icon.
const TECH = [
  { icon: siOpenjdk, name: 'Java 21' },
  { icon: siSpringboot, name: 'Spring Boot' },
  { custom: Bug, name: 'TigerBeetle', hex: 'F5A623' },
  { icon: siPostgresql, name: 'PostgreSQL' },
  { icon: siRedis, name: 'Redis' },
  { icon: siApachekafka, name: 'Kafka' },
  { icon: siFlyway, name: 'Flyway' },
  { icon: siDocker, name: 'Docker' },
  { icon: siCaddy, name: 'Caddy' },
  { icon: siReact, name: 'React' },
  { icon: siTailwindcss, name: 'Tailwind CSS' },
  { icon: siVite, name: 'Vite' },
  { icon: siJunit5, name: 'JUnit 5' },
  { icon: siSwagger, name: 'Swagger' },
  { icon: siApachemaven, name: 'Maven' },
]

/** Very dark brand colours (OpenJDK, Kafka) would vanish on black, so they light up white instead. */
const hoverColor = (hex) => {
  const n = parseInt(hex, 16)
  const luminance = 0.2126 * ((n >> 16) & 255) + 0.7152 * ((n >> 8) & 255) + 0.0722 * (n & 255)
  return luminance < 60 ? '#f4f5f7' : `#${hex}`
}

function Logo({ item }) {
  const hex = item.icon?.hex ?? item.hex
  return (
    <div
      className="group relative grid place-items-center px-7 text-faint transition-colors duration-300 hover:text-[var(--brand)]"
      style={{ '--brand': hoverColor(hex) }}
    >
      {item.custom ? (
        <item.custom className="size-9" aria-label={item.name} role="img" />
      ) : (
        <svg viewBox="0 0 24 24" className="size-9 fill-current" role="img" aria-label={item.name}>
          <title>{item.name}</title>
          <path d={item.icon.path} />
        </svg>
      )}
      {/* Name appears on hover only */}
      <span className="pointer-events-none absolute -bottom-7 rounded-md border border-line bg-raised px-2 py-0.5 text-[11px] whitespace-nowrap text-ink opacity-0 transition-opacity duration-200 group-hover:opacity-100">
        {item.name}
      </span>
    </div>
  )
}

export function TechLogos() {
  return (
    <section aria-label="Technologies used" className="mt-28">
      <p className="text-center text-xs font-semibold tracking-[0.2em] text-faint uppercase">Built with</p>
      <Marquee className="mt-8 pb-8" duration="45s">
        {TECH.map((item) => <Logo key={item.name} item={item} />)}
      </Marquee>
    </section>
  )
}
