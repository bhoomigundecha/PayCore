import { useState } from 'react'
import { Store, UserPlus, Wallet } from 'lucide-react'
import { toast } from 'sonner'
import { useSandbox } from '../../hooks/useSandbox'
import { Button } from '../ui/Button'
import { Field, Segmented, TextInput } from '../ui/Form'

export function CreateAccountForm() {
  const { addAccount } = useSandbox()
  const [type, setType] = useState('CUSTOMER_WALLET')
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    try {
      await addAccount(type, name.trim())
      toast.success(`Created ${name.trim()}`)
      setName('')
    } catch (err) {
      toast.error(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <Segmented
        layoutId="account-type"
        className="w-full"
        value={type}
        onChange={setType}
        options={[
          { value: 'CUSTOMER_WALLET', label: 'Customer', icon: Wallet },
          { value: 'MERCHANT', label: 'Merchant', icon: Store },
        ]}
      />
      <div className="flex gap-2">
        <Field label="Name" className="flex-1">
          {(id) => (
            <TextInput id={id} value={name} maxLength={200} onChange={(e) => setName(e.target.value)} placeholder={type === 'MERCHANT' ? 'e.g. Monsoon Café' : 'e.g. Asha Rao'} />
          )}
        </Field>
        <Button type="submit" variant="secondary" loading={busy} disabled={!name.trim()} className="mt-[22px] h-11">
          <UserPlus className="size-4" /> Create
        </Button>
      </div>
    </form>
  )
}
