from dataclasses import dataclass, field
import numpy as np


@dataclass
class CsiFrame:
    """One CSI snapshot from a source.

    amplitudes/phases shape: (n_subcarriers,)
    """
    timestamp: float
    amplitudes: np.ndarray
    phases: np.ndarray = field(default_factory=lambda: np.zeros(0))
    rssi: float = 0.0

    @property
    def n_subcarriers(self) -> int:
        return self.amplitudes.shape[0]
