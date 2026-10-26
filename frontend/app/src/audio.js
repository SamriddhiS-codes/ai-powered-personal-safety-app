export class Recorder {
  constructor(onLevel) {
    this.onLevel = onLevel;
    this.buf = [];
    this.label = "";
  }

  async start() {
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: true },
    });
    this.label = this.stream.getAudioTracks()[0]?.label || "default microphone";
    this.ctx = new AudioContext();
    await this.ctx.resume(); // browsers can start audio "suspended"
    const src = this.ctx.createMediaStreamSource(this.stream);
    this.proc = this.ctx.createScriptProcessor(4096, 1, 1);
    this.proc.onaudioprocess = (e) => {
      const d = e.inputBuffer.getChannelData(0);
      this.buf.push(new Float32Array(d));
      let s = 0;
      let p = 0;
      for (let i = 0; i < d.length; i++) {
        s += d[i] * d[i];
        const a = Math.abs(d[i]);
        if (a > p) p = a;
      }
      this.onLevel?.({ rms: Math.sqrt(s / d.length), peak: p });
    };
    src.connect(this.proc);
    this.proc.connect(this.ctx.destination);
  }

  // Everything recorded since the last take() as a WAV, or null if under 0.5 s.
  // With lastSeconds, only the most recent lastSeconds of that audio is returned
  // (the rest is discarded), so e.g. a scream just before a spoken reply is left out.
  take(lastSeconds) {
    const rate = this.ctx ? this.ctx.sampleRate : 44100;
    const total = this.buf.reduce((a, b) => a + b.length, 0);
    if (total < rate * 0.5) return null;
    const all = new Float32Array(total);
    let o = 0;
    this.buf.forEach((b) => {
      all.set(b, o);
      o += b.length;
    });
    this.buf = [];
    const f = lastSeconds ? all.subarray(Math.max(0, total - Math.floor(rate * lastSeconds))) : all;
    const n = f.length;
    const v = new DataView(new ArrayBuffer(44 + n * 2));
    const w = (p, s) => [...s].forEach((c, i) => v.setUint8(p + i, c.charCodeAt(0)));
    w(0, "RIFF");
    v.setUint32(4, 36 + n * 2, true);
    w(8, "WAVEfmt ");
    v.setUint32(16, 16, true);
    v.setUint16(20, 1, true);
    v.setUint16(22, 1, true);
    v.setUint32(24, rate, true);
    v.setUint32(28, rate * 2, true);
    v.setUint16(32, 2, true);
    v.setUint16(34, 16, true);
    w(36, "data");
    v.setUint32(40, n * 2, true);
    for (let i = 0; i < n; i++) {
      const x = Math.max(-1, Math.min(1, f[i]));
      v.setInt16(44 + i * 2, x < 0 ? x * 0x8000 : x * 0x7fff, true);
    }
    return new Blob([v], { type: "audio/wav" });
  }

  stop() {
    if (this.proc) {
      this.proc.disconnect();
      this.proc.onaudioprocess = null;
    }
    this.stream?.getTracks().forEach((t) => t.stop());
    if (this.ctx && this.ctx.state !== "closed") this.ctx.close();
    this.proc = this.stream = this.ctx = null;
    this.buf = [];
  }
}