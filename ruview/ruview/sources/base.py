from abc import ABC, abstractmethod
from typing import Iterator
from ..core.csi_frame import CsiFrame


class CsiSource(ABC):
    """Abstract source of CSI frames. All variants implement iter_frames()."""

    @abstractmethod
    def iter_frames(self) -> Iterator[CsiFrame]:
        ...

    def close(self) -> None:
        pass
