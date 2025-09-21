import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'ab-icon',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './icon.html',
  styleUrl: './icon.css'
})
export class Icon {
  @Input() name: string = '';
  @Input() color: 'primary' | 'accent' | 'warn' | undefined = undefined;
}
