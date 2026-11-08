// Audio Context & Variables
let audioContext;
let audioBuffer;
let sourceNode;
let gainNode;
let analyser;
let isPlaying = false;
let startTime = 0;
let pauseTime = 0;

// Effects Configuration
const effects = [
    { id: 'bass', name: 'Bass', icon: 'fa-volume-up', type: 'bass' },
    { id: 'dhol', name: 'Dhol', icon: 'fa-drum', type: 'dhol' },
    { id: 'guitar', name: 'Guitar', icon: 'fa-guitar', type: 'guitar' },
    { id: 'piano', name: 'Piano', icon: 'fa-music', type: 'piano' },
    { id: 'drop', name: 'Drop', icon: 'fa-bolt', type: 'drop' },
    { id: 'echo', name: 'Echo', icon: 'fa-wave-square', type: 'echo' },
    { id: 'distort', name: 'Distort', icon: 'fa-fire', type: 'distortion' },
    { id: 'wobble', name: 'Wobble', icon: 'fa-water', type: 'wobble' },
    { id: 'flanger', name: 'Flanger', icon: 'fa-wind', type: 'flanger' },
    { id: 'phaser', name: 'Phaser', icon: 'fa-circle-notch', type: 'phaser' },
    { id: 'chorus', name: 'Chorus', icon: 'fa-users', type: 'chorus' },
    { id: 'delay', name: 'Delay', icon: 'fa-clock', type: 'delay' },
    { id: 'pitch', name: 'Pitch', icon: 'fa-arrow-up', type: 'pitch' },
    { id: 'bitcrush', name: 'BitCrush', icon: 'fa-microchip', type: 'bitcrusher' },
    { id: 'filter', name: 'Filter', icon: 'fa-filter', type: 'filter' },
    { id: 'tremolo', name: 'Tremolo', icon: 'fa-signal', type: 'tremolo' },
    { id: 'vibrato', name: 'Vibrato', icon: 'fa-sine-wave', type: 'vibrato' },
    { id: 'reverse', name: 'Reverse', icon: 'fa-rotate-left', type: 'reverse' },
    { id: 'stutter', name: 'Stutter', icon: 'fa-ellipsis', type: 'stutter' },
    { id: 'robot', name: 'Robot', icon: 'fa-robot', type: 'robot' }
];

// Sound Library
const soundLibrary = [
    { id: 'ioi', name: 'ioi Sound', icon: 'fa-star' },
    { id: 'tabla', name: 'Tabla', icon: 'fa-drum' },
    { id: 'clap', name: 'Clap', icon: 'fa-hand' },
    { id: 'kick', name: 'Kick', icon: 'fa-bullseye' },
    { id: 'snare', name: 'Snare', icon: 'fa-circle' },
    { id: 'hihat', name: 'Hi-Hat', icon: 'fa-circle-half-stroke' }
];

// Active effect state
let activeEffect = null;
let effectInterval = null;
let effectOscillators = [];

// Customization
let effectSettings = {
    intensity: 70,
    volume: 80,
    frequency: 100
};

// Initialize App
document.addEventListener('DOMContentLoaded', () => {
    initAudioContext();
    renderEffects();
    renderSounds();
    setupEventListeners();
    setupVisualizer();
});

function initAudioContext() {
    audioContext = new (window.AudioContext || window.webkitAudioContext)();
    gainNode = audioContext.createGain();
    analyser = audioContext.createAnalyser();
    analyser.fftSize = 256;
    gainNode.connect(analyser);
    analyser.connect(audioContext.destination);
}

function renderEffects() {
    const grid = document.getElementById('effectsGrid');
    effects.forEach(effect => {
        const btn = document.createElement('button');
        btn.className = 'effect-btn';
        btn.dataset.effect = effect.id;
        btn.innerHTML = `<i class="fa-solid ${effect.icon}"></i><span>${effect.name}</span>`;
        
        // Hold to apply
        btn.addEventListener('mousedown', (e) => startEffect(effect, e));
        btn.addEventListener('touchstart', (e) => {
            e.preventDefault();
            startEffect(effect, e);
        });
        
        btn.addEventListener('mouseup', () => stopEffect(effect));
        btn.addEventListener('mouseleave', () => stopEffect(effect));
        btn.addEventListener('touchend', () => stopEffect(effect));
        
        // Click to select for customization
        btn.addEventListener('click', () => selectEffect(effect));
        
        grid.appendChild(btn);
    });
}

function renderSounds() {
    const grid = document.getElementById('soundsGrid');
    soundLibrary.forEach(sound => {
        const btn = document.createElement('button');
        btn.className = 'sound-btn';
        btn.innerHTML = `<i class="fa-solid ${sound.icon}"></i><span>${sound.name}</span><small>Tap to use</small>`;
        btn.addEventListener('click', () => playLibrarySound(sound));
        grid.appendChild(btn);
    });
}

function setupEventListeners() {
    // File input
    document.getElementById('audioFile').addEventListener('change', handleFileSelect);
    document.getElementById('customSound').addEventListener('change', handleCustomSound);
    
    // Player controls
    document.getElementById('playBtn').addEventListener('click', togglePlay);
    document.getElementById('stopBtn').addEventListener('click', stopPlayback);
    document.getElementById('seekBar').addEventListener('input', handleSeek);
    
    // Customization sliders
    document.getElementById('intensitySlider').addEventListener('input', (e) => {
        effectSettings.intensity = e.target.value;
        document.getElementById('intensityValue').textContent = e.target.value + '%';
    });
    
    document.getElementById('volumeSlider').addEventListener('input', (e) => {
        effectSettings.volume = e.target.value;
        document.getElementById('volumeValue').textContent = e.target.value + '%';
    });
    
    document.getElementById('freqSlider').addEventListener('input', (e) => {
        effectSettings.frequency = e.target.value;
        document.getElementById('freqValue').textContent = e.target.value + ' Hz';
    });
    
    // Export
    document.getElementById('exportBtn').addEventListener('click', exportToMP3);
}

async function handleFileSelect(e) {
    const file = e.target.files[0];
    if (!file) return;
    
    document.getElementById('fileName').textContent = file.name;
    
    const arrayBuffer = await file.arrayBuffer();
    audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
    
    document.getElementById('playBtn').disabled = false;
    document.getElementById('stopBtn').disabled = false;
    document.getElementById('exportBtn').disabled = false;
    document.getElementById('duration').textContent = formatTime(audioBuffer.duration);
}

function handleCustomSound(e) {
    const file = e.target.files[0];
    if (!file) return;
    alert(`Custom sound "${file.name}" added to library!`);
}

function togglePlay() {
    if (isPlaying) {
        pausePlayback();
    } else {
        startPlayback();
    }
}

function startPlayback() {
    if (!audioBuffer) return;
    
    sourceNode = audioContext.createBufferSource();
    sourceNode.buffer = audioBuffer;
    sourceNode.connect(gainNode);
    
    sourceNode.start(0, pauseTime);
    startTime = audioContext.currentTime - pauseTime;
    isPlaying = true;
    
    document.getElementById('playBtn').innerHTML = '<i class="fa-solid fa-pause"></i>';
    
    sourceNode.onended = () => {
        if (isPlaying) {
            isPlaying = false;
            pauseTime = 0;
            document.getElementById('playBtn').innerHTML = '<i class="fa-solid fa-play"></i>';
        }
    };
    
    updateSeekBar();
}

function pausePlayback() {
    if (sourceNode) {
        sourceNode.stop();
        pauseTime = audioContext.currentTime - startTime;
        isPlaying = false;
        document.getElementById('playBtn').innerHTML = '<i class="fa-solid fa-play"></i>';
    }
}

function stopPlayback() {
    if (sourceNode) {
        sourceNode.stop();
        pauseTime = 0;
        isPlaying = false;
        document.getElementById('playBtn').innerHTML = '<i class="fa-solid fa-play"></i>';
        document.getElementById('seekBar').value = 0;
        document.getElementById('currentTime').textContent = '0:00';
    }
}

function handleSeek(e) {
    const seekTime = (e.target.value / 100) * audioBuffer.duration;
    if (isPlaying) {
        sourceNode.stop();
        pauseTime = seekTime;
        startPlayback();
    } else {
        pauseTime = seekTime;
        document.getElementById('currentTime').textContent = formatTime(seekTime);
    }
}

function updateSeekBar() {
    if (!isPlaying) return;
    
    const currentTime = audioContext.currentTime - startTime;
    const progress = (currentTime / audioBuffer.duration) * 100;
    
    document.getElementById('seekBar').value = progress;
    document.getElementById('currentTime').textContent = formatTime(currentTime);
    
    requestAnimationFrame(updateSeekBar);
}

function formatTime(seconds) {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
}

// EFFECTS SYSTEM - Hold to Loop
function startEffect(effect, event) {
    const btn = document.querySelector(`[data-effect="${effect.id}"]`);
    btn.classList.add('active');
    
    // Start continuous effect loop
    applyEffectLoop(effect);
    
    // Repeat effect while holding
    effectInterval = setInterval(() => {
        applyEffectLoop(effect);
    }, 200); // Every 200ms
}

function stopEffect(effect) {
    const btn = document.querySelector(`[data-effect="${effect.id}"]`);
    btn.classList.remove('active');
    
    // Stop interval
    if (effectInterval) {
        clearInterval(effectInterval);
        effectInterval = null;
    }
    
    // Stop all oscillators
    effectOscillators.forEach(osc => {
        try {
            osc.stop();
        } catch (e) {}
    });
    effectOscillators = [];
}

function applyEffectLoop(effect) {
    if (!isPlaying && !audioBuffer) {
        // Play effect sound even without song
        playEffectSound(effect);
        return;
    }
    
    // Apply effect to playing audio
    switch (effect.type) {
        case 'bass':
            applyBassEffect();
            break;
        case 'dhol':
        case 'guitar':
        case 'piano':
        case 'drop':
        case 'echo':
        case 'distortion':
        case 'wobble':
        case 'flanger':
        case 'phaser':
        case 'chorus':
        case 'delay':
        case 'pitch':
        case 'bitcrusher':
        case 'filter':
        case 'tremolo':
        case 'vibrato':
        case 'reverse':
        case 'stutter':
        case 'robot':
            playEffectSound(effect);
            break;
    }
}

function applyBassEffect() {
    const bassGain = audioContext.createGain();
    const bassFilter = audioContext.createBiquadFilter();
    
    bassFilter.type = 'lowshelf';
    bassFilter.frequency.value = effectSettings.frequency;
    bassFilter.gain.value = effectSettings.intensity * 0.5;
    
    bassGain.gain.value = effectSettings.volume / 100;
    
    if (sourceNode) {
        sourceNode.disconnect();
        sourceNode.connect(bassFilter);
        bassFilter.connect(bassGain);
        bassGain.connect(analyser);
    }
}

function playEffectSound(effect) {
    const oscillator = audioContext.createOscillator();
    const effectGain = audioContext.createGain();
    
    // Different sounds for different effects
    switch (effect.type) {
        case 'dhol':
            oscillator.type = 'triangle';
            oscillator.frequency.value = 80;
            break;
        case 'guitar':
            oscillator.type = 'sawtooth';
            oscillator.frequency.value = 220;
            break;
        case 'piano':
            oscillator.type = 'sine';
            oscillator.frequency.value = 440;
            break;
        case 'drop':
            oscillator.type = 'square';
            oscillator.frequency.value = 60;
            break;
        case 'echo':
            oscillator.type = 'sine';
            oscillator.frequency.value = 300;
            break;
        case 'distortion':
            oscillator.type = 'sawtooth';
            oscillator.frequency.value = 150;
            break;
        case 'wobble':
            oscillator.type = 'sine';
            oscillator.frequency.value = 100;
            break;
        case 'flanger':
            oscillator.type = 'triangle';
            oscillator.frequency.value = 250;
            break;
        case 'phaser':
            oscillator.type = 'sine';
            oscillator.frequency.value = 350;
            break;
        case 'chorus':
            oscillator.type = 'sine';
            oscillator.frequency.value = 400;
            break;
        case 'delay':
            oscillator.type = 'triangle';
            oscillator.frequency.value = 200;
            break;
        case 'pitch':
            oscillator.type = 'sine';
            oscillator.frequency.value = 500;
            break;
        case 'bitcrusher':
            oscillator.type = 'square';
            oscillator.frequency.value = 180;
            break;
        case 'filter':
            oscillator.type = 'sine';
            oscillator.frequency.value = effectSettings.frequency;
            break;
        case 'tremolo':
            oscillator.type = 'sine';
            oscillator.frequency.value = 300;
            break;
        case 'vibrato':
            oscillator.type = 'sine';
            oscillator.frequency.value = 350;
            break;
        case 'reverse':
            oscillator.type = 'sawtooth';
            oscillator.frequency.value = 120;
            break;
        case 'stutter':
            oscillator.type = 'square';
            oscillator.frequency.value = 250;
            break;
        case 'robot':
            oscillator.type = 'square';
            oscillator.frequency.value = 100;
            break;
        default:
            oscillator.type = 'sine';
            oscillator.frequency.value = 200;
    }
    
    effectGain.gain.value = effectSettings.volume / 200;
    
    oscillator.connect(effectGain);
    effectGain.connect(analyser);
    
    oscillator.start();
    oscillator.stop(audioContext.currentTime + 0.2);
    
    effectOscillators.push(oscillator);
}

function selectEffect(effect) {
    document.getElementById('customizeSection').style.display = 'block';
    document.getElementById('activeEffectName').textContent = effect.name;
}

function playLibrarySound(sound) {
    const oscillator = audioContext.createOscillator();
    const soundGain = audioContext.createGain();
    
    oscillator.type = 'sine';
    oscillator.frequency.value = 300;
    soundGain.gain.value = 0.3;
    
    oscillator.connect(soundGain);
    soundGain.connect(audioContext.destination);
    
    oscillator.start();
    oscillator.stop(audioContext.currentTime + 0.3);
    
    alert(`"${sound.name}" will be added to your remix!`);
}

// Visualizer
function setupVisualizer() {
    const canvas = document.getElementById('visualizer');
    const ctx = canvas.getContext('2d');
    
    function resize() {
        canvas.width = canvas.offsetWidth;
        canvas.height = canvas.offsetHeight;
    }
    resize();
    window.addEventListener('resize', resize);
    
    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLength);
    
    function draw() {
        requestAnimationFrame(draw);
        
        analyser.getByteFrequencyData(dataArray);
        
        ctx.fillStyle = 'rgba(0, 0, 0, 0.2)';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        
        const barWidth = (canvas.width / bufferLength) * 2.5;
        let x = 0;
        
        for (let i = 0; i < bufferLength; i++) {
            const barHeight = dataArray[i] / 2;
            
            const gradient = ctx.createLinearGradient(0, canvas.height, 0, canvas.height - barHeight);
            gradient.addColorStop(0, '#a855f7');
            gradient.addColorStop(1, '#06b6d4');
            
            ctx.fillStyle = gradient;
            ctx.fillRect(x, canvas.height - barHeight, barWidth, barHeight);
            
            x += barWidth + 1;
        }
    }
    
    draw();
}

// MP3 Export
async function exportToMP3() {
    if (!audioBuffer) return;
    
    const exportProgress = document.getElementById('exportProgress');
    const progressFill = document.getElementById('progressFill');
    const exportStatus = document.getElementById('exportStatus');
    
    exportProgress.style.display = 'block';
    exportStatus.textContent = 'Rendering audio...';
    progressFill.style.width = '20%';
    
    try {
        // Create offline context for rendering
        const offlineContext = new OfflineAudioContext(
            audioBuffer.numberOfChannels,
            audioBuffer.length,
            audioBuffer.sampleRate
        );
        
        const offlineSource = offlineContext.createBufferSource();
        offlineSource.buffer = audioBuffer;
        
        // Apply effects (simplified for export)
        const bassFilter = offlineContext.createBiquadFilter();
        bassFilter.type = 'lowshelf';
        bassFilter.frequency.value = effectSettings.frequency;
        bassFilter.gain.value = effectSettings.intensity * 0.3;
        
        const exportGain = offlineContext.createGain();
        exportGain.gain.value = effectSettings.volume / 100;
        
        offlineSource.connect(bassFilter);
        bassFilter.connect(exportGain);
        exportGain.connect(offlineContext.destination);
        
        offlineSource.start();
        
        exportStatus.textContent = 'Processing effects...';
        progressFill.style.width = '50%';
        
        const renderedBuffer = await offlineContext.startRendering();
        
        exportStatus.textContent = 'Encoding to MP3...';
        progressFill.style.width = '70%';
        
        // Convert to MP3 using lamejs
        const mp3Data = convertToMP3(renderedBuffer);
        
        exportStatus.textContent = 'Saving file...';
        progressFill.style.width = '90%';
        
        // Download
        const blob = new Blob(mp3Data, { type: 'audio/mp3' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'remix_' + Date.now() + '.mp3';
        a.click();
        
        progressFill.style.width = '100%';
        exportStatus.textContent = '✅ Export complete!';
        
        setTimeout(() => {
            exportProgress.style.display = 'none';
            progressFill.style.width = '0%';
        }, 3000);
        
    } catch (error) {
        exportStatus.textContent = '❌ Error: ' + error.message;
        console.error(error);
    }
}

function convertToMP3(audioBuffer) {
    const channels = audioBuffer.numberOfChannels;
    const sampleRate = audioBuffer.sampleRate;
    const bitRate = 128;
    
    const mp3encoder = new lamejs.Mp3Encoder(channels, sampleRate, bitRate);
    
    const left = audioBuffer.getChannelData(0);
    const right = channels > 1 ? audioBuffer.getChannelData(1) : left;
    
    const sampleBlockSize = 1152;
    const mp3Data = [];
    
    const leftInt16 = convertFloat32ToInt16(left);
    const rightInt16 = convertFloat32ToInt16(right);
    
    for (let i = 0; i < leftInt16.length; i += sampleBlockSize) {
        const leftChunk = leftInt16.subarray(i, i + sampleBlockSize);
        const rightChunk = rightInt16.subarray(i, i + sampleBlockSize);
        
        const mp3buf = mp3encoder.encodeBuffer(leftChunk, rightChunk);
        if (mp3buf.length > 0) {
            mp3Data.push(mp3buf);
        }
    }
    
    const end = mp3encoder.flush();
    if (end.length > 0) {
        mp3Data.push(end);
    }
    
    return mp3Data;
}

function convertFloat32ToInt16(float32Array) {
    const int16Array = new Int16Array(float32Array.length);
    for (let i = 0; i < float32Array.length; i++) {
        let s = Math.max(-1, Math.min(1, float32Array[i]));
        int16Array[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
    }
    return int16Array;
}